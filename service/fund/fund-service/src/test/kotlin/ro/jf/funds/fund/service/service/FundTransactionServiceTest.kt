package ro.jf.funds.fund.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ro.jf.funds.account.api.model.*
import ro.jf.funds.account.sdk.AccountTransactionSdk
import ro.jf.funds.commons.event.Producer
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.fund.api.model.CreateFundRecordTO
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import java.math.BigDecimal
import java.util.UUID.randomUUID

class FundTransactionServiceTest {
    private val accountTransactionSdk: AccountTransactionSdk = mock()
    private val accountTransactionsRequestProducer = mock<Producer<CreateAccountTransactionsTO>>()
    private val accountTransactionAdapter =
        AccountTransactionAdapter(accountTransactionSdk, accountTransactionsRequestProducer)
    private val fundTransactionService = FundTransactionService(accountTransactionAdapter)

    @Test
    fun `given create valid transaction`(): Unit = runBlocking {
        val userId = randomUUID()
        val dateTime = LocalDateTime.parse("2021-09-01T12:00:00")
        val companyAccountId = randomUUID()
        val personalAccountId = randomUUID()
        val workFundId = randomUUID()
        val expensesFundId = randomUUID()
        val transactionId = randomUUID()
        val record1Id = randomUUID()
        val record2Id = randomUUID()
        val request = CreateFundTransactionTO(
            dateTime = dateTime,
            records = listOf(
                CreateFundRecordTO(
                    fundId = workFundId,
                    accountId = companyAccountId,
                    unit = Currency.RON,
                    amount = BigDecimal("-100.25"),
                ),
                CreateFundRecordTO(
                    fundId = expensesFundId,
                    accountId = personalAccountId,
                    unit = Currency.RON,
                    amount = BigDecimal("100.25"),
                )
            )
        )
        val expectedCreateAccountTransactionRequest = CreateAccountTransactionTO(
            dateTime = dateTime,
            records = listOf(
                CreateAccountRecordTO(
                    accountId = companyAccountId,
                    amount = BigDecimal("-100.25"),
                    unit = Currency.RON,
                    properties = propertiesOf(METADATA_FUND_ID to workFundId.toString())
                ),
                CreateAccountRecordTO(
                    accountId = personalAccountId,
                    amount = BigDecimal("100.25"),
                    unit = Currency.RON,
                    properties = propertiesOf(METADATA_FUND_ID to expensesFundId.toString())
                )
            ),
            properties = propertiesOf()
        )
        whenever(accountTransactionSdk.createTransaction(userId, expectedCreateAccountTransactionRequest)).thenReturn(
            AccountTransactionTO(
                id = transactionId,
                dateTime = dateTime,
                records = listOf(
                    AccountRecordTO(
                        id = record1Id,
                        accountId = companyAccountId,
                        amount = BigDecimal("-100.25"),
                        unit = Currency.RON,
                        properties = propertiesOf(METADATA_FUND_ID to workFundId.toString())
                    ),
                    AccountRecordTO(
                        id = record2Id,
                        accountId = personalAccountId,
                        amount = BigDecimal("100.25"),
                        unit = Currency.RON,
                        properties = propertiesOf(METADATA_FUND_ID to expensesFundId.toString())
                    )
                ),
                properties = propertiesOf()
            )
        )

        val transaction = fundTransactionService.createTransaction(userId, request)

        assertThat(transaction.id).isEqualTo(transactionId)
        assertThat(transaction.userId).isEqualTo(userId)
        assertThat(transaction.dateTime).isEqualTo(dateTime)
        assertThat(transaction.records).hasSize(2)
        assertThat(transaction.records[0].id).isEqualTo(record1Id)
        assertThat(transaction.records[0].fundId).isEqualTo(workFundId)
        assertThat(transaction.records[0].accountId).isEqualTo(companyAccountId)
        assertThat(transaction.records[0].amount).isEqualTo(BigDecimal("-100.25"))
        assertThat(transaction.records[1].id).isEqualTo(record2Id)
        assertThat(transaction.records[1].fundId).isEqualTo(expensesFundId)
        assertThat(transaction.records[1].accountId).isEqualTo(personalAccountId)
        assertThat(transaction.records[1].amount).isEqualTo(BigDecimal("100.25"))
    }

    @Test
    fun `given list transactions`(): Unit = runBlocking {
        val userId = randomUUID()
        val transactionId = randomUUID()
        val record1Id = randomUUID()
        val record2Id = randomUUID()
        val account1Id = randomUUID()
        val account2Id = randomUUID()
        val fund1Id = randomUUID()
        val fund2Id = randomUUID()
        val rawTransactionTime = "2021-09-01T12:00:00"
        val transactionTime = LocalDateTime.parse(rawTransactionTime)
        whenever(accountTransactionSdk.listTransactions(userId, TransactionsFilterTO.empty())).thenReturn(
            ListTO(
                listOf(
                    AccountTransactionTO(
                        id = transactionId,
                        dateTime = transactionTime,
                        records = listOf(
                            AccountRecordTO(
                                id = record1Id,
                                accountId = account1Id,
                                amount = BigDecimal(100.25),
                                unit = Currency.RON,
                                properties = propertiesOf("fundId" to fund1Id.toString()),
                            ),
                            AccountRecordTO(
                                id = record2Id,
                                accountId = account2Id,
                                amount = BigDecimal(50.75),
                                unit = Currency.RON,
                                properties = propertiesOf("fundId" to fund2Id.toString()),
                            )
                        ),
                        properties = propertiesOf()
                    )
                )
            )
        )

        val transactions = fundTransactionService.listTransactions(userId)

        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().id).isEqualTo(transactionId)
        assertThat(transactions.first().userId).isEqualTo(userId)
        assertThat(transactions.first().dateTime).isEqualTo(transactionTime)
        assertThat(transactions.first().records).hasSize(2)
        assertThat(transactions.first().records[0].id).isEqualTo(record1Id)
        assertThat(transactions.first().records[0].fundId).isEqualTo(fund1Id)
        assertThat(transactions.first().records[0].accountId).isEqualTo(account1Id)
        assertThat(transactions.first().records[0].amount).isEqualTo(BigDecimal(100.25))
        assertThat(transactions.first().records[1].id).isEqualTo(record2Id)
        assertThat(transactions.first().records[1].fundId).isEqualTo(fund2Id)
        assertThat(transactions.first().records[1].accountId).isEqualTo(account2Id)
        assertThat(transactions.first().records[1].amount).isEqualTo(BigDecimal(50.75))
    }

    @Test
    fun `given delete transaction by id`(): Unit = runBlocking {
        val userId = randomUUID()
        val transactionId = randomUUID()
        fundTransactionService.deleteTransaction(userId, transactionId)
        verify(accountTransactionSdk).deleteTransaction(userId, transactionId)
    }
}
