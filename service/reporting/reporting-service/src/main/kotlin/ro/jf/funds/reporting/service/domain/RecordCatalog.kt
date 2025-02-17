package ro.jf.funds.reporting.service.domain

import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.reporting.api.model.DateInterval
import ro.jf.funds.reporting.api.model.GranularDateInterval

class RecordCatalog(
    private val reportRecords: List<ReportRecord>,
    private val granularInterval: GranularDateInterval,
) {
    val previousRecords: Map<FinancialUnit, List<ReportRecord>>

    private val recordsGrouped: Map<FinancialUnit, Map<DateInterval, List<ReportRecord>>>

    init {
        val (previousRecords, intervalRecords) =
            splitRecordsBeforeAndDuring(reportRecords, granularInterval)
        this.previousRecords = previousRecords
        this.recordsGrouped = intervalRecords
            .mapValues { (_, records) ->
                records.groupBy { granularInterval.getBucket(it.date) }
            }
    }

    fun getRecordsByBucket(bucket: DateInterval): Map<FinancialUnit, List<ReportRecord>> {
        return recordsGrouped.mapValues { (_, recordsByBucket) ->
            recordsByBucket[bucket] ?: emptyList()
        }
    }

    private fun splitRecordsBeforeAndDuring(
        records: List<ReportRecord>,
        interval: GranularDateInterval,
    ): Pair<Map<FinancialUnit, List<ReportRecord>>, Map<FinancialUnit, List<ReportRecord>>> {
        return records.partition { it.date < interval.interval.from }
            .let { (previous, interval) ->
                groupByUnit(previous) to groupByUnit(interval)
            }
    }

    private fun groupByUnit(records: List<ReportRecord>): Map<FinancialUnit, List<ReportRecord>> {
        return records.groupBy { it.unit }
            .mapValues { it.value.sortedBy { record -> record.date } }
    }
}
