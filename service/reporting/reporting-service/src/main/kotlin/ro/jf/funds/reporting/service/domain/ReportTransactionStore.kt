package ro.jf.funds.reporting.service.domain

import kotlinx.coroutines.Deferred
import java.util.*

data class ReportTransactionStore(
    private val fundId: UUID,
    private val previousTransactions: Deferred<List<ReportTransaction>>,
    private val bucketTransactions: Map<TimeBucket, Deferred<List<ReportTransaction>>>,
) {
    suspend fun getPreviousTransactions(): List<ReportTransaction> = previousTransactions.await()
    suspend fun getBucketTransactions(bucket: TimeBucket): List<ReportTransaction> =
        bucketTransactions[bucket]?.await() ?: emptyList()

    suspend fun getPreviousRecords(): List<ReportRecord> =
        getPreviousTransactions().extractFundRecords().toList()

    suspend fun getBucketRecords(bucket: TimeBucket): List<ReportRecord> =
        getBucketTransactions(bucket).extractFundRecords().toList()

    suspend fun getPreviousRecordsByUnit(): ByUnit<List<ReportRecord>> =
        getPreviousTransactions().extractFundRecords().groupBy { it.unit }

    suspend fun getBucketRecordsByUnit(bucket: TimeBucket): ByUnit<List<ReportRecord>> =
        getBucketTransactions(bucket).extractFundRecords().groupBy { it.unit }

    private fun List<ReportTransaction>.extractFundRecords(): Sequence<ReportRecord> =
        this.asSequence()
            .flatMap { it.records }
            .filter { it.fundId == fundId }
}
