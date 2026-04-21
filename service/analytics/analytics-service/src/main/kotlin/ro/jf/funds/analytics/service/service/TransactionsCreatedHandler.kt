package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.Uuid
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.service.domain.AnalyticsRecord
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.fund.api.model.TransactionTO
import ro.jf.funds.fund.api.model.TransactionsCreatedTO
import ro.jf.funds.platform.jvm.event.Event
import ro.jf.funds.platform.jvm.event.EventHandler

private val log = logger { }

class TransactionsCreatedHandler(
    private val analyticsRecordRepository: AnalyticsRecordRepository,
) : EventHandler<TransactionsCreatedTO> {
    override suspend fun handle(event: Event<TransactionsCreatedTO>) {
        log.info { "Received transactions created event. userId = ${event.userId}, transactions = ${event.payload.transactions.size}" }
        val userId = Uuid.fromString(event.userId.toString())
        val analyticsRecords = event.payload.transactions.flatMap { it.toAnalyticsRecords(userId) }
        analyticsRecordRepository.saveAll(analyticsRecords)
        log.info { "Persisted ${analyticsRecords.size} analytics records." }
    }

    private fun TransactionTO.toAnalyticsRecords(userId: Uuid): List<AnalyticsRecord> =
        records.map { record ->
            AnalyticsRecord(
                id = record.id,
                userId = userId,
                transactionId = id,
                dateTime = dateTime,
                accountId = record.accountId,
                fundId = record.fundId,
                amount = record.amount,
                unit = record.unit,
                transactionType = type,
                category = record.category,
            )
        }
}
