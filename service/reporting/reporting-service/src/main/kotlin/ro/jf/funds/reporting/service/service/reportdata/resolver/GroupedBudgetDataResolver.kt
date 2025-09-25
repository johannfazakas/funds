package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.observability.tracing.withSpan
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.reporting.service.domain.Budget
import ro.jf.funds.reporting.service.domain.ByBucket
import ro.jf.funds.reporting.service.domain.ByGroup
import ro.jf.funds.reporting.service.domain.ByUnit
import ro.jf.funds.reporting.service.domain.GroupedBudgetReportConfiguration
import ro.jf.funds.reporting.service.domain.ReportGroup
import ro.jf.funds.reporting.service.domain.ReportRecord
import ro.jf.funds.reporting.service.domain.TimeBucket
import ro.jf.funds.reporting.service.domain.YearMonth
import ro.jf.funds.reporting.service.domain.merge
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import ro.jf.funds.reporting.service.domain.ReportDataForecastInput
import ro.jf.funds.reporting.service.service.reportdata.resolver.ReportDataResolver
import ro.jf.funds.reporting.service.domain.ReportDataResolverInput
import java.math.BigDecimal
import java.math.MathContext
import java.util.UUID

class GroupedBudgetDataResolver(
    private val conversionRateService: ConversionRateService,
) : ReportDataResolver<ByGroup<Budget>> {
    override suspend fun resolve(input: ReportDataResolverInput): ByBucket<ByGroup<Budget>> = withSuspendingSpan {
        val previousLeftBudgets = getPreviousGroupedBudget(input)
        input.interval
            .generateBucketedData(previousLeftBudgets) { timeBucket, previous ->
                getNextGroupedBudget(input, timeBucket, previous)
            }
            .mergeBucketedData(input)
    }

    override suspend fun forecast(input: ReportDataForecastInput<ByGroup<Budget>>): ByBucket<ByGroup<Budget>> =
        withSpan("forecast") {
            input.interval
                .generateForecastData(
                    input.forecastConfiguration.inputBuckets,
                    input.realData
                ) { inputBuckets: List<ByGroup<Budget>> ->
                    forecastData(inputBuckets, input.groups)
                }
        }

    private suspend fun Map<TimeBucket, ByGroup<ByUnit<Budget>>>.mergeBucketedData(
        input: ReportDataResolverInput,
    ): ByBucket<ByGroup<Budget>> = withSuspendingSpan {
        mapValues { (timeBucket, budgetByUnitByGroup) ->
            budgetByUnitByGroup.mergeUnits(timeBucket.to, input)
        }
    }

    private suspend fun ByGroup<ByUnit<Budget>>.mergeUnits(
        until: LocalDate,
        input: ReportDataResolverInput,
    ): ByGroup<Budget> = mapValues { (_, budgetByUnit) ->
        budgetByUnit.convertToSingleCurrency(
            input.userId,
            until,
            input.dataConfiguration.currency,
        )
    }

    private suspend fun getPreviousGroupedBudget(
        input: ReportDataResolverInput,
    ): ByGroup<ByUnit<Budget>> = withSuspendingSpan {
        input.reportTransactionStore.getPreviousRecords()
            // split by year month for monthly normalization
            .groupBy { YearMonth(it.date.year, it.date.month.value) }
            .entries.sortedBy { it.key }
            .map { it.key to it.value }
            .fold(emptyMap()) { acc, (yearMonth, monthRecords) ->
                getGroupedBudget(
                    input, monthRecords, yearMonth.endDate, acc
                )
            }
    }

    private suspend fun getNextGroupedBudget(
        input: ReportDataResolverInput,
        interval: TimeBucket,
        previous: ByGroup<ByUnit<Budget>>,
    ): ByGroup<ByUnit<Budget>> = withSuspendingSpan {
        getGroupedBudget(
            input, input.reportTransactionStore.getBucketRecords(interval), interval.to, previous
        )
    }

    private fun forecastData(
        inputBuckets: List<ByGroup<Budget>>,
        groups: List<String>,
    ): ByGroup<Budget> {
        val inputSize = inputBuckets.size.toBigDecimal()
        return groups
            .associateWith { group ->
                val groupBudgets = inputBuckets.mapNotNull { it[group] }
                val allocated = groupBudgets.sumOf { it.allocated }.divide(inputSize, MathContext.DECIMAL64)
                val spent = groupBudgets.sumOf { it.spent }.divide(inputSize, MathContext.DECIMAL64)
                val left = groupBudgets.last().left + allocated + spent
                Budget(allocated, spent, left)
            }
    }

    private suspend fun getGroupedBudget(
        input: ReportDataResolverInput,
        records: List<ReportRecord>,
        intervalEnd: LocalDate,
        previousBudget: ByGroup<ByUnit<Budget>>,
    ): ByGroup<ByUnit<Budget>> = withSuspendingSpan {
        records
            .fold(previousBudget.resetAllocatedAndSpentAmount()) { budget, record ->
                budget.addRecord(input, record)
            }
            .normalizeGroupCurrencyRatio(
                input.userId, intervalEnd, input.dataConfiguration.currency
            )
    }

    private fun ByGroup<ByUnit<Budget>>.addRecord(
        input: ReportDataResolverInput,
        record: ReportRecord,
    ): ByGroup<ByUnit<Budget>> {
        val matchingGroup = getMatchingGroup(record, input.dataConfiguration.groups ?: emptyList())
        return if (matchingGroup != null) {
            addGroupExpense(matchingGroup.name, record)
        } else {
            allocateIncome(record, input.dataConfiguration.reports.groupedBudget.getDistributionByDate(record.date))
        }
    }

    private fun ByGroup<ByUnit<Budget>>.allocateIncome(
        record: ReportRecord,
        distribution: GroupedBudgetReportConfiguration.BudgetDistribution,
    ): ByGroup<ByUnit<Budget>> = withSpan("allocateIncome") {
        val allocatedRecordIncome: ByGroup<ByUnit<Budget>> = distribution.groups
            .associate { (group, percentage) ->
                group to mapOf(
                    record.unit to record.amount.percentage(percentage).let { Budget(it, BigDecimal.ZERO, it) })
            }
        allocatedRecordIncome.merge(this) { a, b -> a.merge(b) { x, y -> x + y } }
    }

    private fun ByGroup<ByUnit<Budget>>.addGroupExpense(
        matchingGroup: String,
        record: ReportRecord,
    ): ByGroup<ByUnit<Budget>> = withSpan("addGroupExpense") {
        val recordGroupExpense =
            mapOf(matchingGroup to mapOf(record.unit to Budget(BigDecimal.ZERO, record.amount, record.amount)))
        this.merge(recordGroupExpense) { a, b -> a.merge(b) { x, y -> x + y } }
    }

    private suspend fun ByGroup<ByUnit<Budget>>.normalizeGroupCurrencyRatio(
        userId: UUID,
        date: LocalDate,
        reportCurrency: Currency,
    ): ByGroup<ByUnit<Budget>> = withSuspendingSpan {
        val leftByUnit = this
            .flatMap { it.value.entries }
            .groupBy({ it.key }) { it.value.left }
            .mapValues { (_, left) -> left.sumOf { it } }

        val mapValues = this
            .mapValues { (_, budgetByUnit) ->
                val convertedGroupLeftValue = budgetByUnit.entries
                    .sumOf { (unit, budget) ->
                        budget.left * conversionRateService.getRate(userId, date, unit, reportCurrency)
                    }
                // X * W1 * R1 + X * W2 * R2 = T => X (W1 * R1 + W2 * R2) = T => X = T / (W1 * R1 + W2 * R2)
                val multiplicationFactor = budgetByUnit.keys
                    .sumOf { unit ->
                        leftByUnit[unit]!! * conversionRateService.getRate(userId, date, unit, reportCurrency)
                    }
                    .let { convertedGroupLeftValue.divide(it, MathContext.DECIMAL64) }
                budgetByUnit.mapValues { (unit, budget) ->
                    budget.copy(left = multiplicationFactor * leftByUnit[unit]!!)
                }
            }
        mapValues
    }

    private suspend fun ByUnit<Budget>.convertToSingleCurrency(
        userId: UUID,
        date: LocalDate,
        reportCurrency: Currency,
    ): Budget =
        this.mapToSingleCurrencyBudget(userId, reportCurrency, date).mergeBudgets()

    private suspend fun ByUnit<Budget>.mapToSingleCurrencyBudget(
        userId: UUID,
        reportCurrency: Currency,
        date: LocalDate,
    ): List<Budget> = withSuspendingSpan {
        this
            .map { (unit, budget) ->
                val rate = conversionRateService.getRate(userId, date, unit, reportCurrency)
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

    private suspend fun ByGroup<ByUnit<Budget>>.resetAllocatedAndSpentAmount(
    ): ByGroup<ByUnit<Budget>> = withSuspendingSpan {
        this.mapValues { (_, byUnit) ->
            byUnit.mapValues { (_, budget) -> Budget(BigDecimal.ZERO, BigDecimal.ZERO, budget.left) }
        }
    }
}