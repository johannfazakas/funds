package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.generateBucketedData
import ro.jf.funds.reporting.service.service.generateForecastData
import java.math.BigDecimal
import java.math.MathContext

private val MATH_PRECISION = MathContext.DECIMAL32

class GroupedBudgetDataResolver : ReportDataResolver<ByGroup<Budget>> {
    override fun resolve(input: ReportDataResolverInput): ByBucket<ByGroup<Budget>>? {
        val groupedBudgetFeature = input.dataConfiguration.features.groupedBudget
        val groups = input.dataConfiguration.groups
        if (!groupedBudgetFeature.enabled || groups.isNullOrEmpty()) return null

        val reportCatalog = input.catalog
        val previousLeftBudgets =
            getGroupedBudget(
                reportCatalog.getPreviousRecords(), ByGroup(), input, groupedBudgetFeature
            )

        val generateBucketedData = input.dateInterval
            .generateBucketedData(
                { interval ->
                    getGroupedBudget(
                        reportCatalog.getBucketRecords(interval), previousLeftBudgets, input, groupedBudgetFeature
                    )
                },
                { interval, previous ->
                    getGroupedBudget(
                        reportCatalog.getBucketRecords(interval), previous, input, groupedBudgetFeature
                    )
                }
            )
        return generateBucketedData
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

    override fun forecast(input: ReportDataForecastInput<ByGroup<Budget>>): ByBucket<ByGroup<Budget>>? {
        return input.dateInterval.generateForecastData(
            input.forecastConfiguration.forecastBuckets,
            input.forecastConfiguration.forecastInputBuckets,
            { interval -> input.realData[interval] }
        ) { inputBuckets: List<ByGroup<Budget>> ->
            val size = inputBuckets.size.toBigDecimal()
            input.groups
                .associateWith { group ->
                    val groupBudgets = inputBuckets.mapNotNull { it[group] }
                    Budget(
                        allocated = groupBudgets.sumOf { it.allocated }.divide(size),
                        left = groupBudgets.sumOf { it.left }.divide(size)
                    )
                }.let { ByGroup(it) }
        }.let { ByBucket(it) }
    }

    private fun getGroupedBudget(
        records: List<ReportRecord>,
        previousBudget: ByGroup<ByUnit<Budget>>,
        input: ReportDataResolverInput,
        feature: GroupedBudgetReportFeature,
    ): ByGroup<ByUnit<Budget>> = records
        .fold(previousBudget.resetAllocatedAmount()) { budget, record -> budget.addRecord(record, input, feature) }

    private fun ByGroup<ByUnit<Budget>>.addRecord(
        record: ReportRecord,
        input: ReportDataResolverInput,
        feature: GroupedBudgetReportFeature,
    ): ByGroup<ByUnit<Budget>> {
        val matchingGroup = getMatchingGroup(record, input.dataConfiguration.groups ?: emptyList())
        return if (matchingGroup != null) {
            addGroupExpense(matchingGroup.name, record, input.dataConfiguration.currency, input.conversions)
        } else {
            allocateIncome(record, feature.getDistributionByDate(record.date))
        }
    }

    private fun ByGroup<ByUnit<Budget>>.allocateIncome(
        record: ReportRecord,
        distribution: GroupedBudgetReportFeature.BudgetDistribution,
    ): ByGroup<ByUnit<Budget>> {
        return distribution.groups
            .map { (group, percentage) ->
                group to ByUnit(record.unit to record.amount.percentage(percentage).let { Budget(it, it) })
            }
            .let { ByGroup(it.toMap()) }
            .let { it.plus(this) { a, b -> a.plus(b) { x, y -> x + y } } }
    }

    private fun ByGroup<ByUnit<Budget>>.addGroupExpense(
        matchingGroup: String,
        record: ReportRecord,
        reportCurrency: Currency,
        conversions: ConversionsResponse,
    ): ByGroup<ByUnit<Budget>> {
        return ByGroup(matchingGroup to ByUnit(record.unit to Budget(BigDecimal.ZERO, record.amount)))
            .let { it.plus(this) { a, b -> a.plus(b) { x, y -> x + y } } }
            // TODO(Johann) this could be done once at the end of the bucket as an optimization. currency conversion should also be available then
            .normalizeGroupCurrencyRatio(record.date, reportCurrency, conversions)
    }

    private fun ByGroup<ByUnit<Budget>>.normalizeGroupCurrencyRatio(
        date: LocalDate,
        reportCurrency: Currency,
        conversions: ConversionsResponse,
    ): ByGroup<ByUnit<Budget>> {
        val leftByUnit = this
            .flatMap { it.value }
            .map { (unit, budget) -> unit to budget.left }
            .groupBy(Pair<FinancialUnit, BigDecimal>::first, Pair<FinancialUnit, BigDecimal>::second)
            .mapValues { (_, left) -> left.sumOf { it } }

        val mapValues = this.mapValues { (_, budgetByUnit) ->
            val convertedGroupLeftValue = budgetByUnit
                .sumOf { (unit, budget) ->
                    budget.left * getConversionRate(date, unit, reportCurrency, conversions)
                }
            // X * W1 * R1 + X * W2 * R2 = T => X (W1 * R1 + W2 * R2) = T => X = T / (W1 * R1 + W2 * R2)
            val multiplicationFactor = budgetByUnit
                .sumOf { (unit, _) ->
                    leftByUnit[unit]!! * getConversionRate(date, unit, reportCurrency, conversions)
                }
                .let { convertedGroupLeftValue.divide(it, MATH_PRECISION) }
            budgetByUnit.mapValues { (unit, budget) ->
                budget.copy(left = multiplicationFactor * leftByUnit[unit]!!)
            }
        }
        return mapValues
    }

    private fun ByUnit<Budget>.convertToSingleCurrency(
        date: LocalDate,
        reportCurrency: Currency,
        conversions: ConversionsResponse,
    ): Budget = this
        .map { (unit, budget) ->
            val rate = if (unit == reportCurrency)
                BigDecimal.ONE
            else
                getConversionRate(date, unit, reportCurrency, conversions)
            Budget(budget.allocated * rate, budget.left * rate)
        }
        .fold(Budget(BigDecimal.ZERO, BigDecimal.ZERO)) { acc, budget -> acc + budget }

    private fun getMatchingGroup(record: ReportRecord, groups: List<ReportGroup>): ReportGroup? {
        return groups.find { it.filter.test(record) }
    }

    private fun BigDecimal.percentage(percentage: Int): BigDecimal {
        return this * BigDecimal(percentage) / BigDecimal(100)
    }

    private fun ByGroup<ByUnit<Budget>>.resetAllocatedAmount(): ByGroup<ByUnit<Budget>> {
        return this.mapValues { (_, byUnit) ->
            byUnit.mapValues { (_, budget) -> Budget(BigDecimal.ZERO, budget.left) }
        }
    }

    private fun getConversionRate(
        date: LocalDate, sourceUnit: FinancialUnit, targetCurrency: Currency, conversions: ConversionsResponse,
    ): BigDecimal =
        if (sourceUnit == targetCurrency)
            BigDecimal.ONE
        else
            conversions.getRate(sourceUnit, targetCurrency, date)
                ?: error("No conversion rate found for $sourceUnit to $targetCurrency at $date")
}
