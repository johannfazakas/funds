package ro.jf.funds.analytics.service.service

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import ro.jf.funds.analytics.service.domain.AnalyticsRecord
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.fund.api.model.TransactionRecordTO
import ro.jf.funds.fund.api.model.TransactionTO
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.fund.api.model.TransactionsCreatedTO
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Label
import ro.jf.funds.platform.jvm.event.Event
import java.util.UUID.randomUUID

class TransactionsCreatedHandlerTest {
    private val analyticsRecordRepository = mock<AnalyticsRecordRepository>()
    private val handler = TransactionsCreatedHandler(analyticsRecordRepository)

    private val userId = randomUUID()
    private val transactionId = randomUUID()
    private val record1Id = randomUUID()
    private val record2Id = randomUUID()
    private val accountId = randomUUID()
    private val fundId = randomUUID()
    private val dateTime = LocalDateTime.parse("2024-01-15T10:30:00")

    @Test
    fun `given transfer transaction - when handling event - then persists flattened analytics records`(): Unit = runBlocking {
        val transaction = TransactionTO.Transfer(
            id = transactionId,
            userId = userId,
            externalId = "ext-1",
            dateTime = dateTime,
            sourceRecord = TransactionRecordTO.CurrencyRecord(
                id = record1Id,
                accountId = accountId,
                fundId = fundId,
                amount = BigDecimal.parseString("-100.50"),
                unit = Currency("RON"),
                labels = listOf(Label("salary")),
            ),
            destinationRecord = TransactionRecordTO.CurrencyRecord(
                id = record2Id,
                accountId = accountId,
                fundId = fundId,
                amount = BigDecimal.parseString("100.50"),
                unit = Currency("RON"),
                labels = emptyList(),
            ),
        )
        val event = Event(userId, TransactionsCreatedTO(listOf(transaction)))

        handler.handle(event)

        val captor = argumentCaptor<List<AnalyticsRecord>>()
        verify(analyticsRecordRepository).saveAll(captor.capture())
        val savedRecords = captor.firstValue
        assertThat(savedRecords).hasSize(2)
        assertThat(savedRecords[0].id).isEqualTo(record1Id)
        assertThat(savedRecords[0].userId).isEqualTo(userId)
        assertThat(savedRecords[0].transactionId).isEqualTo(transactionId)
        assertThat(savedRecords[0].transactionType).isEqualTo(TransactionType.TRANSFER)
        assertThat(savedRecords[0].dateTime).isEqualTo(dateTime)
        assertThat(savedRecords[0].amount).isEqualTo(BigDecimal.parseString("-100.50"))
        assertThat(savedRecords[0].unit).isEqualTo(Currency("RON"))
        assertThat(savedRecords[0].labels).containsExactly(Label("salary"))
        assertThat(savedRecords[1].id).isEqualTo(record2Id)
        assertThat(savedRecords[1].amount).isEqualTo(BigDecimal.parseString("100.50"))
        assertThat(savedRecords[1].labels).isEmpty()
    }
}
