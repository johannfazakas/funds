package ro.jf.funds.reporting.service.domain

import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.reporting.api.model.DateInterval
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.service.service.getBucket

class RecordCatalog(
    reportRecords: List<ReportRecord>,
    private val granularInterval: GranularDateInterval,
) {
    val previousRecords: ByUnit<List<ReportRecord>>

    private val recordsGrouped: ByUnit<Map<DateInterval, List<ReportRecord>>>

    init {
        val (previousRecords, intervalRecords) =
            splitRecordsBeforeAndDuring(reportRecords, granularInterval)
        this.previousRecords = previousRecords
        this.recordsGrouped = intervalRecords
            .iterator().asSequence()
            .map { (unit, records) ->
                unit to records.groupBy { granularInterval.getBucket(it.date) }

            }
            .let { ByUnit(it.toMap()) }
    }

    // TODO(Johann-14) could add caching to this method
    fun getRecordsByBucket(bucket: DateInterval): ByUnit<List<ReportRecord>> =
        recordsGrouped
            .iterator().asSequence()
            .associate { (unit, recordsByBucket) ->
                unit to (recordsByBucket[bucket] ?: emptyList())
            }
            .let { it: Map<FinancialUnit, List<ReportRecord>> -> ByUnit(it) }

    private fun splitRecordsBeforeAndDuring(
        records: List<ReportRecord>,
        interval: GranularDateInterval,
    ): Pair<ByUnit<List<ReportRecord>>, ByUnit<List<ReportRecord>>> {
        return records.partition { it.date < interval.interval.from }
            .let { (previous, interval) ->
                groupByUnit(previous) to groupByUnit(interval)
            }
    }

    private fun groupByUnit(records: List<ReportRecord>): ByUnit<List<ReportRecord>> {
        return records.groupBy { it.unit }
            .mapValues { it.value.sortedBy { record -> record.date } }
            .let { ByUnit(it) }
    }
}
