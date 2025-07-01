package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.utils.getConversionRate
import ro.jf.funds.reporting.service.utils.withSpan
import java.math.BigDecimal
import java.math.MathContext

class GroupedBudgetDataResolver : ReportDataResolver<ByGroup<Budget>> {
    override fun resolve(input: ReportDataResolverInput): ByBucket<ByGroup<Budget>>? = withSpan("resolve") {
        val groupedBudgetFeature = input.dataConfiguration.reports.groupedBudget
        val groups = input.dataConfiguration.groups
        if (!groupedBudgetFeature.enabled || groups.isNullOrEmpty()) return@withSpan null

        val reportCatalog = input.catalog
        val previousLeftBudgets = getPreviousGroupedBudget(reportCatalog, input, groupedBudgetFeature)

        val generateBucketedData = input.interval
            .generateBucketedData(
                { timeBucket ->
                    getSeedGroupedBudget(reportCatalog, timeBucket, previousLeftBudgets, input, groupedBudgetFeature)
                },
                { interval, previous ->
                    getNextGroupedBudget(reportCatalog, interval, previous, input, groupedBudgetFeature)
                }
            )
        mergeBucketedData(generateBucketedData, input)
    }

    private fun mergeBucketedData(
        generateBucketedData: Map<TimeBucket, ByGroup<ByUnit<Budget>>>,
        input: ReportDataResolverInput,
    ): ByBucket<ByGroup<Budget>> = withSpan("mergeBucketedData") {
        generateBucketedData
            .mapValues { (interval, budgetByUnitByGroup) ->
                budgetByUnitByGroup.mapValues { (_, budgetByUnit) ->
                    budgetByUnit.convertToSingleCurrency(
                        interval.to,
                        input.dataConfiguration.currency,
                        input.conversions
                    )
                }
            }
            .let(::ByBucket)
    }

    private fun getPreviousGroupedBudget(
        reportCatalog: RecordCatalog,
        input: ReportDataResolverInput,
        groupedBudgetFeature: GroupedBudgetReportConfiguration,
    ): ByGroup<ByUnit<Budget>> = withSpan("getPreviousGroupedBudget") {

        val records = reportCatalog.getPreviousRecords()
        if (records.isEmpty()) {
            return@withSpan ByGroup(emptyMap())
        }

        records
            // split by year month for monthly normalization
            .groupBy { YearMonth(it.date.year, it.date.month.value) }
            .entries.sortedBy { it.key }
            .fold(ByGroup()) { acc, (yearMonth, monthRecords) ->
                getGroupedBudget(
                    monthRecords,
                    yearMonth.asTimeBucket().to,
                    acc,
                    input,
                    groupedBudgetFeature
                )
            }
    }

    private fun getSeedGroupedBudget(
        reportCatalog: RecordCatalog,
        timeBucket: TimeBucket,
        previousLeftBudgets: ByGroup<ByUnit<Budget>>,
        input: ReportDataResolverInput,
        groupedBudgetFeature: GroupedBudgetReportConfiguration,
    ): ByGroup<ByUnit<Budget>> = withSpan("getSeedGroupedBudget") {
        getGroupedBudget(
            reportCatalog.getBucketRecords(timeBucket), timeBucket.to, previousLeftBudgets, input, groupedBudgetFeature
        )
    }

    private fun getNextGroupedBudget(
        reportCatalog: RecordCatalog,
        interval: TimeBucket,
        previous: ByGroup<ByUnit<Budget>>,
        input: ReportDataResolverInput,
        groupedBudgetFeature: GroupedBudgetReportConfiguration,
    ): ByGroup<ByUnit<Budget>> = withSpan("getNextGroupedBudget", "interval" to interval) {
        getGroupedBudget(
            reportCatalog.getBucketRecords(interval), interval.to, previous, input, groupedBudgetFeature
        )
    }

    override fun forecast(input: ReportDataForecastInput<ByGroup<Budget>>): ByBucket<ByGroup<Budget>>? =
        withSpan("forecast") {
            input.interval.generateForecastData(
                input.forecastConfiguration.inputBuckets,
                { interval -> input.realData[interval] }
            ) { inputBuckets: List<ByGroup<Budget>> ->
                val inputSize = inputBuckets.size.toBigDecimal()
                input.groups
                    .associateWith { group ->
                        val groupBudgets = inputBuckets.mapNotNull { it[group] }
                        val allocated = groupBudgets.sumOf { it.allocated }.divide(inputSize, MathContext.DECIMAL64)
                        val spent = groupBudgets.sumOf { it.spent }.divide(inputSize, MathContext.DECIMAL64)
                        val left = groupBudgets.last().left + allocated + spent
                        Budget(allocated, spent, left)
                    }.let { ByGroup(it) }
            }.let { ByBucket(it) }
        }

    private fun getGroupedBudget(
        records: List<ReportRecord>,
        intervalEnd: LocalDate,
        previousBudget: ByGroup<ByUnit<Budget>>,
        input: ReportDataResolverInput,
        feature: GroupedBudgetReportConfiguration,
    ): ByGroup<ByUnit<Budget>> = withSpan("getGroupedBudget") {
        records
            .fold(previousBudget.resetAllocatedAndSpentAmount()) { budget, record ->
                budget.addRecord(
                    record,
                    input,
                    feature
                )
            }
            .normalizeGroupCurrencyRatio(
                intervalEnd, input.dataConfiguration.currency, input.conversions
            )
    }

    private fun ByGroup<ByUnit<Budget>>.addRecord(
        record: ReportRecord,
        input: ReportDataResolverInput,
        feature: GroupedBudgetReportConfiguration,
    ): ByGroup<ByUnit<Budget>> {
        val matchingGroup = getMatchingGroup(record, input.dataConfiguration.groups ?: emptyList())
        return if (matchingGroup != null) {
            addGroupExpense(matchingGroup.name, record)
        } else {
            allocateIncome(record, feature.getDistributionByDate(record.date))
        }
    }

    private fun ByGroup<ByUnit<Budget>>.allocateIncome(
        record: ReportRecord,
        distribution: GroupedBudgetReportConfiguration.BudgetDistribution,
    ): ByGroup<ByUnit<Budget>> = withSpan("allocateIncome") {
        distribution.groups
            .map { (group, percentage) ->
                group to ByUnit(
                    record.unit to record.amount.percentage(percentage).let { Budget(it, BigDecimal.ZERO, it) })
            }
            .let { ByGroup(it.toMap()) }
            .let { it.plus(this) { a, b -> a.plus(b) { x, y -> x + y } } }
    }

    private fun ByGroup<ByUnit<Budget>>.addGroupExpense(
        matchingGroup: String,
        record: ReportRecord,
    ): ByGroup<ByUnit<Budget>> = withSpan("addGroupExpense") {
        ByGroup(matchingGroup to ByUnit(record.unit to Budget(BigDecimal.ZERO, record.amount, record.amount)))
            .let { it.plus(this) { a, b -> a.plus(b) { x, y -> x + y } } }
    }

    private fun ByGroup<ByUnit<Budget>>.normalizeGroupCurrencyRatio(
        date: LocalDate,
        reportCurrency: Currency,
        conversions: ConversionsResponse,
    ): ByGroup<ByUnit<Budget>> = withSpan("normalizeGroupCurrencyRatio") {
        val leftByUnit = this
            .flatMap { it.value }
            .map { (unit, budget) -> unit to budget.left }
            .groupBy(Pair<FinancialUnit, BigDecimal>::first, Pair<FinancialUnit, BigDecimal>::second)
            .mapValues { (_, left) -> left.sumOf { it } }

        val mapValues = this.mapValues { (_, budgetByUnit) ->
            val convertedGroupLeftValue = budgetByUnit
                .sumOf { (unit, budget) ->
                    budget.left * getConversionRate(conversions, date, unit, reportCurrency)
                }
            // X * W1 * R1 + X * W2 * R2 = T => X (W1 * R1 + W2 * R2) = T => X = T / (W1 * R1 + W2 * R2)
            val multiplicationFactor = budgetByUnit
                .sumOf { (unit, _) ->
                    leftByUnit[unit]!! * getConversionRate(conversions, date, unit, reportCurrency)
                }
                .let { convertedGroupLeftValue.divide(it, MathContext.DECIMAL64) }
            budgetByUnit.mapValues { (unit, budget) ->
                budget.copy(left = multiplicationFactor * leftByUnit[unit]!!)
            }
        }
        mapValues
    }

    private fun ByUnit<Budget>.convertToSingleCurrency(
        date: LocalDate,
        reportCurrency: Currency,
        conversions: ConversionsResponse,
    ): Budget = withSpan("convertToSingleCurrency") {
        mapToSingleCurrencyBudget(reportCurrency, date, conversions).mergeBudgets()
    }

    private fun ByUnit<Budget>.mapToSingleCurrencyBudget(
        reportCurrency: Currency,
        date: LocalDate,
        conversions: ConversionsResponse,
    ): List<Budget> = withSpan("mapToSingleCurrencyBudget") {
        this
            .map { (unit, budget) ->
                val rate = getConversionRate(conversions, date, unit, reportCurrency)
                Budget(budget.allocated * rate, budget.spent * rate, budget.left * rate)
            }
    }

    private fun List<Budget>.mergeBudgets(): Budget = withSpan("mergeBudgets") {
        fold(Budget(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)) { acc, budget ->
            acc + budget
        }
    }

    private fun getMatchingGroup(record: ReportRecord, groups: List<ReportGroup>): ReportGroup? {
        return groups.find { it.filter.test(record) }
    }

    private fun BigDecimal.percentage(percentage: Int): BigDecimal {
        return this * BigDecimal(percentage) / BigDecimal(100)
    }

    private fun ByGroup<ByUnit<Budget>>.resetAllocatedAndSpentAmount(
    ): ByGroup<ByUnit<Budget>> = withSpan("resetAllocatedAmount") {
        this.mapValues { (_, byUnit) ->
            byUnit.mapValues { (_, budget) -> Budget(BigDecimal.ZERO, BigDecimal.ZERO, budget.left) }
        }
    }
}
