package ro.jf.funds.reporting.service.domain

import ro.jf.funds.reporting.api.model.DateInterval
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.service.service.getBucket

class RecordCatalog(
    reportRecords: List<ReportRecord>,
    private val granularInterval: GranularDateInterval,
) {
    private val previousRecords: List<ReportRecord>
    private val previousRecordsGroupedByUnit: ByUnit<List<ReportRecord>>

    private val intervalRecords: List<ReportRecord>

    private val intervalRecordsGroupedByBucket: ByBucket<List<ReportRecord>>
    private val intervalRecordsGroupedByBucketByUnit: ByBucket<ByUnit<List<ReportRecord>>>

    init {
        splitRecordsBeforeAndDuring(reportRecords, granularInterval).also { (previous, interval) ->
            previousRecords = previous
            previousRecordsGroupedByUnit = ByUnit(previous.groupBy { it.unit })
            intervalRecords = interval
        }

        intervalRecordsGroupedByBucket = intervalRecords.asSequence<ReportRecord>()
            .groupBy<ReportRecord, DateInterval> { granularInterval.getBucket(it.date) }
            .let { ByBucket<List<ReportRecord>>(it) }

        intervalRecordsGroupedByBucketByUnit = intervalRecordsGroupedByBucket.iterator().asSequence()
            .map { (bucket, records) -> bucket to ByUnit(records.groupBy { it.unit }) }
            .toMap().let { ByBucket(it) }
    }

    fun getPreviousRecords(): List<ReportRecord> = previousRecords

    fun getPreviousRecordsGroupedByUnit(): ByUnit<List<ReportRecord>> = previousRecordsGroupedByUnit

    fun getBucketRecords(bucket: DateInterval): List<ReportRecord> =
        intervalRecordsGroupedByBucket[bucket] ?: emptyList()

    fun getBucketRecordsGroupedByUnit(bucket: DateInterval): ByUnit<List<ReportRecord>> =
        intervalRecordsGroupedByBucketByUnit[bucket] ?: ByUnit(emptyMap())

    private fun splitRecordsBeforeAndDuring(
        records: List<ReportRecord>,
        interval: GranularDateInterval,
    ): Pair<List<ReportRecord>, List<ReportRecord>> = records.partition { it.date < interval.interval.from }
}
