package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.service.generateBucketedData
import java.math.BigDecimal

class GroupedBudgetDataResolver : ReportDataResolver<ByGroup<ByUnit<Budget>>> {
    override fun resolve(input: ReportDataResolverInput): ByBucket<ByGroup<ByUnit<Budget>>>? {
        val groupedBudgetFeature = input.dataConfiguration.features.groupedBudget
        if (!groupedBudgetFeature.enabled || input.dataConfiguration.groups.isNullOrEmpty()) {
            return null
        }
        val previousLeftBudgets =
            calculatePreviousLeftBudgets(input.catalog, input.dataConfiguration.groups, groupedBudgetFeature)
        return input.dateInterval
            .generateBucketedData(
                { interval ->
                    getGroupedBudget(
                        input.catalog.getRecordsByBucket(interval),
                        input.dataConfiguration.groups,
                        previousLeftBudgets,
                        groupedBudgetFeature
                    )
                },
                { interval, previous ->
                    getGroupedBudget(
                        input.catalog.getRecordsByBucket(interval),
                        input.dataConfiguration.groups,
                        previous,
                        groupedBudgetFeature
                    )
                }
            )
            .let(::ByBucket)
    }

    private fun getGroupedBudget(
        records: ByUnit<List<ReportRecord>>,
        groups: List<ReportGroup>,
        previousLeft: ByGroup<ByUnit<Budget>>,
        feature: GroupedBudgetReportFeature,
    ): ByGroup<ByUnit<Budget>> {
        val previousRecords = previousLeft
            .mapValues { (_, byUnit) -> byUnit.mapValues { (_, left) -> Budget(BigDecimal.ZERO, left.left) } }
        val newRecords = records.asSequence()
            .flatMap { (unit, records) ->
                records.asSequence().flatMap { record ->
                    val group = getMatchingGroup(record, groups)
                    if (group != null) {
                        listOf(ByGroup(group.name to ByUnit(unit to Budget(BigDecimal.ZERO, record.amount))))
                    } else {
                        feature.getDistributionByDate(record.date).groups
                            .map { (group, percentage) ->
                                ByGroup(
                                    group to ByUnit(
                                        unit to record.amount.percentage(percentage).let { Budget(it, it) })
                                )
                            }
                    }
                }
            }
        val newRecordsMerged = newRecords
            .fold(previousRecords) { acc, groupedData ->
                acc.plus(groupedData) { a, b -> a.plus(b) { x, y -> x + y } }
            }
        return newRecordsMerged
    }

    private fun calculatePreviousLeftBudgets(
        reportCatalog: RecordCatalog,
        groups: List<ReportGroup>,
        feature: GroupedBudgetReportFeature,
    ): ByGroup<ByUnit<Budget>> {
        val previousRecords = reportCatalog.previousRecords

        val previousLeftBudget = previousRecords
            .asSequence()
            .flatMap { (unit, records) ->
                records.asSequence().flatMap { record ->
                    val group = getMatchingGroup(record, groups)
                    if (group != null) {
                        listOf(ByGroup(group.name to ByUnit(unit to Budget(BigDecimal.ZERO, record.amount))))
                    } else {
                        feature.getDistributionByDate(record.date).groups
                            .map { (group, percentage) ->
                                ByGroup(
                                    group to ByUnit(
                                        unit to Budget(
                                            BigDecimal.ZERO,
                                            record.amount.percentage(percentage)
                                        )
                                    )
                                )
                            }
                    }
                }
            }
        val previousLeftBudgetMerged = previousLeftBudget
            .fold(ByGroup<ByUnit<Budget>>()) { acc, groupedData ->
                acc.plus(groupedData) { a, b -> a.plus(b) { x, y -> x + y } }
            }

        return previousLeftBudgetMerged
    }

    private fun getMatchingGroup(record: ReportRecord, groups: List<ReportGroup>): ReportGroup? {
        return groups.find { it.filter.test(record) }
    }

    private fun BigDecimal.percentage(percentage: Int): BigDecimal {
        return this * BigDecimal(percentage) / BigDecimal(100)
    }
}
