package ro.jf.funds.fund.service.service

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.fund.api.model.RecordSortField
import ro.jf.funds.fund.service.domain.RecordFilter
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.fund.service.domain.Record
import ro.jf.funds.fund.service.persistence.RecordRepository
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Label
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.SortOrder
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.platform.jvm.persistence.PagedResult
import java.util.UUID.randomUUID

class RecordServiceTest {
    private val recordRepository = mock<RecordRepository>()
    private val recordService = RecordService(recordRepository)

    private val userId = randomUUID()
    private val transactionId = randomUUID()
    private val accountId = randomUUID()
    private val fundId = randomUUID()
    private val recordId = randomUUID()
    private val dateTime = LocalDateTime.parse("2021-09-01T12:00:00")

    @Test
    fun `given no filter - when list records - then returns all records`(): Unit = runBlocking {
        val record = Record.CurrencyRecord(
            transactionId = transactionId, dateTime = dateTime,
            id = recordId,
            accountId = accountId,
            fundId = fundId,
            amount = BigDecimal.parseString("100.50"),
            unit = Currency("RON"),
            labels = listOf(Label("groceries"))
        )
        whenever(recordRepository.list(userId, null, null, null))
            .thenReturn(PagedResult(listOf(record), 1L))

        val result = recordService.listRecords(userId, null, null, null)

        assertThat(result.items).hasSize(1)
        assertThat(result.total).isEqualTo(1L)
        assertThat(result.items.first()).isEqualTo(record)
    }

    @Test
    fun `given filter by account - when list records - then delegates filter to repository`(): Unit = runBlocking {
        val filter = RecordFilter(accountId = accountId)
        val record = Record.CurrencyRecord(
            transactionId = transactionId, dateTime = dateTime,
            id = recordId,
            accountId = accountId,
            fundId = fundId,
            amount = BigDecimal.parseString("50.00"),
            unit = Currency("EUR"),
            labels = emptyList()
        )
        whenever(recordRepository.list(userId, filter, null, null))
            .thenReturn(PagedResult(listOf(record), 1L))

        val result = recordService.listRecords(userId, filter, null, null)

        assertThat(result.items).hasSize(1)
        assertThat(result.items.first().accountId).isEqualTo(accountId)
    }

    @Test
    fun `given filter by fund - when list records - then delegates filter to repository`(): Unit = runBlocking {
        val filter = RecordFilter(fundId = fundId)
        val record = Record.CurrencyRecord(
            transactionId = transactionId, dateTime = dateTime,
            id = recordId,
            accountId = accountId,
            fundId = fundId,
            amount = BigDecimal.parseString("75.25"),
            unit = Currency("USD"),
            labels = emptyList()
        )
        whenever(recordRepository.list(userId, filter, null, null))
            .thenReturn(PagedResult(listOf(record), 1L))

        val result = recordService.listRecords(userId, filter, null, null)

        assertThat(result.items).hasSize(1)
        assertThat(result.items.first().fundId).isEqualTo(fundId)
    }

    @Test
    fun `given filter by unit - when list records - then delegates filter to repository`(): Unit = runBlocking {
        val filter = RecordFilter(unit = "RON")
        val record = Record.CurrencyRecord(
            transactionId = transactionId, dateTime = dateTime,
            id = recordId,
            accountId = accountId,
            fundId = fundId,
            amount = BigDecimal.parseString("200.00"),
            unit = Currency("RON"),
            labels = emptyList()
        )
        whenever(recordRepository.list(userId, filter, null, null))
            .thenReturn(PagedResult(listOf(record), 1L))

        val result = recordService.listRecords(userId, filter, null, null)

        assertThat(result.items).hasSize(1)
    }

    @Test
    fun `given filter by label - when list records - then delegates filter to repository`(): Unit = runBlocking {
        val filter = RecordFilter(label = "groceries")
        val record = Record.CurrencyRecord(
            transactionId = transactionId, dateTime = dateTime,
            id = recordId,
            accountId = accountId,
            fundId = fundId,
            amount = BigDecimal.parseString("150.00"),
            unit = Currency("RON"),
            labels = listOf(Label("groceries"))
        )
        whenever(recordRepository.list(userId, filter, null, null))
            .thenReturn(PagedResult(listOf(record), 1L))

        val result = recordService.listRecords(userId, filter, null, null)

        assertThat(result.items).hasSize(1)
        assertThat(result.items.first().labels).contains(Label("groceries"))
    }

    @Test
    fun `given pagination - when list records - then delegates pagination to repository`(): Unit = runBlocking {
        val pageRequest = PageRequest(offset = 10, limit = 5)
        val record = Record.CurrencyRecord(
            transactionId = transactionId, dateTime = dateTime,
            id = recordId,
            accountId = accountId,
            fundId = fundId,
            amount = BigDecimal.parseString("100.00"),
            unit = Currency("RON"),
            labels = emptyList()
        )
        whenever(recordRepository.list(userId, null, pageRequest, null))
            .thenReturn(PagedResult(listOf(record), 100L))

        val result = recordService.listRecords(userId, null, pageRequest, null)

        assertThat(result.items).hasSize(1)
        assertThat(result.total).isEqualTo(100L)
    }

    @Test
    fun `given sort by date descending - when list records - then delegates sorting to repository`(): Unit = runBlocking {
        val sortRequest = SortRequest(RecordSortField.DATE, SortOrder.DESC)
        val record = Record.CurrencyRecord(
            transactionId = transactionId, dateTime = dateTime,
            id = recordId,
            accountId = accountId,
            fundId = fundId,
            amount = BigDecimal.parseString("100.00"),
            unit = Currency("RON"),
            labels = emptyList()
        )
        whenever(recordRepository.list(userId, null, null, sortRequest))
            .thenReturn(PagedResult(listOf(record), 1L))

        val result = recordService.listRecords(userId, null, null, sortRequest)

        assertThat(result.items).hasSize(1)
    }

    @Test
    fun `given sort by amount ascending - when list records - then delegates sorting to repository`(): Unit = runBlocking {
        val sortRequest = SortRequest(RecordSortField.AMOUNT, SortOrder.ASC)
        val record = Record.CurrencyRecord(
            transactionId = transactionId, dateTime = dateTime,
            id = recordId,
            accountId = accountId,
            fundId = fundId,
            amount = BigDecimal.parseString("100.00"),
            unit = Currency("RON"),
            labels = emptyList()
        )
        whenever(recordRepository.list(userId, null, null, sortRequest))
            .thenReturn(PagedResult(listOf(record), 1L))

        val result = recordService.listRecords(userId, null, null, sortRequest)

        assertThat(result.items).hasSize(1)
    }

    @Test
    fun `given combined filter, pagination and sorting - when list records - then delegates all to repository`(): Unit = runBlocking {
        val filter = RecordFilter(accountId = accountId, unit = "RON")
        val pageRequest = PageRequest(offset = 0, limit = 10)
        val sortRequest = SortRequest(RecordSortField.DATE, SortOrder.DESC)
        val record = Record.CurrencyRecord(
            transactionId = transactionId, dateTime = dateTime,
            id = recordId,
            accountId = accountId,
            fundId = fundId,
            amount = BigDecimal.parseString("100.00"),
            unit = Currency("RON"),
            labels = emptyList()
        )
        whenever(recordRepository.list(userId, filter, pageRequest, sortRequest))
            .thenReturn(PagedResult(listOf(record), 50L))

        val result = recordService.listRecords(userId, filter, pageRequest, sortRequest)

        assertThat(result.items).hasSize(1)
        assertThat(result.total).isEqualTo(50L)
    }
}
