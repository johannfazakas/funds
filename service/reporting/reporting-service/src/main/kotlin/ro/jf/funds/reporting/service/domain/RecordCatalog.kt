package ro.jf.funds.reporting.service.domain

class RecordCatalog(
    reportRecords: List<ReportRecord>,
    private val reportDataInterval: ReportDataInterval,
) {
    private val previousRecords: List<ReportRecord>
    private val previousRecordsGroupedByUnit: ByUnit<List<ReportRecord>>

    private val intervalRecords: List<ReportRecord>

    private val intervalRecordsGroupedByBucket: ByBucket<List<ReportRecord>>
    private val intervalRecordsGroupedByBucketByUnit: ByBucket<ByUnit<List<ReportRecord>>>

    init {
        splitRecordsBeforeAndDuring(reportRecords, reportDataInterval).also { (previous, interval) ->
            previousRecords = previous
            previousRecordsGroupedByUnit = ByUnit(previous.groupBy { it.unit })
            intervalRecords = interval
        }

        intervalRecordsGroupedByBucket = intervalRecords.asSequence<ReportRecord>()
            .groupBy<ReportRecord, TimeBucket> { reportDataInterval.getBucket(it.date) }
            .let { ByBucket<List<ReportRecord>>(it) }

        intervalRecordsGroupedByBucketByUnit = intervalRecordsGroupedByBucket.iterator().asSequence()
            .map { (bucket, records) -> bucket to ByUnit(records.groupBy { it.unit }) }
            .toMap().let { ByBucket(it) }
    }

    fun getPreviousRecords(): List<ReportRecord> = previousRecords

    fun getPreviousRecordsGroupedByUnit(): ByUnit<List<ReportRecord>> = previousRecordsGroupedByUnit

    fun getBucketRecords(bucket: TimeBucket): List<ReportRecord> =
        intervalRecordsGroupedByBucket[bucket] ?: emptyList()

    fun getBucketRecordsGroupedByUnit(bucket: TimeBucket): ByUnit<List<ReportRecord>> =
        intervalRecordsGroupedByBucketByUnit[bucket] ?: ByUnit(emptyMap())

    private fun splitRecordsBeforeAndDuring(
        records: List<ReportRecord>,
        interval: ReportDataInterval,
    ): Pair<List<ReportRecord>, List<ReportRecord>> = records.partition { it.date < interval.fromDate }
}
