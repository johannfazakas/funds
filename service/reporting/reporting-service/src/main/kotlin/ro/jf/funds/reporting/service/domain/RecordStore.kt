package ro.jf.funds.reporting.service.domain

import kotlinx.coroutines.Deferred

data class RecordStore(
    private val previousRecords: Deferred<List<ReportRecord>>,
    private val bucketRecords: Map<TimeBucket, Deferred<List<ReportRecord>>>,
) {
    suspend fun getPreviousRecords(): List<ReportRecord> = previousRecords.await()
    suspend fun getBucketRecords(bucket: TimeBucket): List<ReportRecord> = bucketRecords[bucket]?.await() ?: emptyList()

    suspend fun getPreviousRecordsByUnit(): ByUnit<List<ReportRecord>> =
        ByUnit(previousRecords.await().groupBy { it.unit })

    suspend fun getBucketRecordsByUnit(bucket: TimeBucket): ByUnit<List<ReportRecord>> =
        ByUnit(getBucketRecords(bucket).groupBy { it.unit })
}
