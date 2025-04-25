package ro.jf.funds.reporting.service.service.reportdata.resolver

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.generateBucketedData
import java.math.BigDecimal
import java.math.MathContext

private val MATH_PRECISION = MathContext.DECIMAL32

class GroupedBudgetDataResolver : ReportDataResolver<ByGroup<ByUnit<Budget>>> {
    override fun resolve(input: ReportDataResolverInput): ByBucket<ByGroup<ByUnit<Budget>>>? {
        val groupedBudgetFeature = input.dataConfiguration.features.groupedBudget
        val groups = input.dataConfiguration.groups
        if (!groupedBudgetFeature.enabled || groups.isNullOrEmpty()) return null

        val reportCatalog = input.catalog
        val previousLeftBudgets =
            getGroupedBudget(
                // TODO(Johann-14) rething parameters, maybe dataConfiguration should be passed
                reportCatalog.previousRecords,
                groups,
                ByGroup(),
                input.conversions,
                groupedBudgetFeature,
                input.dataConfiguration.currency
            )

        return input.dateInterval
            .generateBucketedData(
                { interval ->
                    getGroupedBudget(
                        reportCatalog.getByBucket(interval),
                        groups,
                        previousLeftBudgets,
                        input.conversions,
                        groupedBudgetFeature,
                        input.dataConfiguration.currency
                    )
                },
                { interval, previous ->
                    getGroupedBudget(
                        reportCatalog.getByBucket(interval),
                        groups,
                        previous,
                        input.conversions,
                        groupedBudgetFeature,
                        input.dataConfiguration.currency
                    )
                }
            )
            .let(::ByBucket)
    }

    private fun getGroupedBudget(
        // TODO(Johann-14) why are records grouped by units if they are mixed again afterwards?
        records: ByUnit<List<ReportRecord>>,
        groups: List<ReportGroup>,
        previousBudget: ByGroup<ByUnit<Budget>>,
        conversions: ConversionsResponse,
        feature: GroupedBudgetReportFeature,
        currency: Currency,
    ): ByGroup<ByUnit<Budget>> {
        val flattenRecords = records.asSequence()
            .flatMap { (_, records) -> records.asSequence() }
            .sortedBy { it.date }
        val budget = flattenRecords
            .fold(previousBudget.resetAllocatedAmount()) { budget, record ->
                budget.addRecord(
                    record,
                    groups,
                    conversions,
                    feature,
                    currency,
                )
            }
        return budget
    }

    private fun ByGroup<ByUnit<Budget>>.addRecord(
        record: ReportRecord,
        // TODO(Johann-14) not sure about these arguments. are they needed in this form?
        groups: List<ReportGroup>,
        conversions: ConversionsResponse,
        feature: GroupedBudgetReportFeature,
        reportCurrency: Currency,
    ): ByGroup<ByUnit<Budget>> {
        return getMatchingGroup(record, groups)
            ?.let { matchingGroup -> addGroupExpense(matchingGroup.name, record, reportCurrency, conversions) }
            ?: allocateIncome(record, feature.getDistributionByDate(record.date))
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
            // TODO(Johann-14) what if normalization would happen just once at the end of a bucket?
            .normalizeGroupCurrencyRatio(record.date, reportCurrency, conversions)
    }

    private fun ByGroup<ByUnit<Budget>>.normalizeGroupCurrencyRatio(
        date: LocalDate,
        reportCurrency: Currency,
        conversions: ConversionsResponse,
    ): ByGroup<ByUnit<Budget>> {
        // TODO(Johann-14) but maybe this map could contain converted value rates?
        val leftWeightByUnit = this
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
                    leftWeightByUnit[unit]!! * getConversionRate(date, unit, reportCurrency, conversions)
                }
                .let { convertedGroupLeftValue.divide(it, MATH_PRECISION) }
            budgetByUnit.mapValues { (unit, budget) ->
                budget.copy(left = multiplicationFactor * leftWeightByUnit[unit]!!)
            }
        }

        return mapValues
    }

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
