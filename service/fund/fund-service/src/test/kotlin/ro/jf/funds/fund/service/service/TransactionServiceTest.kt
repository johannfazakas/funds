package ro.jf.funds.fund.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.fund.service.domain.Account
import ro.jf.funds.fund.service.domain.Fund
import ro.jf.funds.fund.service.domain.Transaction
import ro.jf.funds.fund.service.domain.TransactionRecord
import ro.jf.funds.fund.service.persistence.AccountRepository
import ro.jf.funds.fund.service.persistence.FundRepository
import ro.jf.funds.fund.service.persistence.TransactionRepository
import java.math.BigDecimal
import java.util.UUID.randomUUID

class TransactionServiceTest {
    private val transactionRepository = mock<TransactionRepository>()
    private val accountRepository = mock<AccountRepository>()
    private val fundRepository = mock<FundRepository>()

    private val fundService = FundService(fundRepository)
    private val accountService = AccountService(accountRepository)
    private val transactionService = TransactionService(transactionRepository, fundService, accountService)

    private val userId = randomUUID()

    private val rawTransactionTime = "2021-09-01T12:00:00"
    private val transactionTime = LocalDateTime.parse(rawTransactionTime)

    private val companyAccountId = randomUUID()
    private val personalAccountId = randomUUID()

    private val workFundId = randomUUID()
    private val expensesFundId = randomUUID()

    private val transactionId = randomUUID()
    private val transactionExternalId = randomUUID().toString()

    private val record1Id = randomUUID()
    private val record2Id = randomUUID()

    @Test
    fun `given create valid transaction`(): Unit = runBlocking {
        whenever(accountRepository.findById(userId, companyAccountId)).thenReturn(
            Account(companyAccountId, userId, AccountName("Company"), FinancialUnit.of("currency", "RON"))
        )
        whenever(accountRepository.findById(userId, personalAccountId)).thenReturn(
            Account(personalAccountId, userId, AccountName("Personal"), FinancialUnit.of("currency", "RON"))
        )
        val request = CreateTransactionTO(
            dateTime = transactionTime,
            externalId = transactionExternalId,
            type = TransactionType.SINGLE_RECORD,
            records = listOf(
                CreateTransactionRecordTO(
                    accountId = companyAccountId,
                    fundId = workFundId,
                    amount = BigDecimal("-100.25"),
                    unit = FinancialUnit.of("currency", "RON"),
                    labels = listOf(Label("one"), Label("two"))
                ),
                CreateTransactionRecordTO(
                    accountId = personalAccountId,
                    fundId = expensesFundId,
                    amount = BigDecimal("100.25"),
                    unit = FinancialUnit.of("currency", "RON")
                )
            )
        )
        whenever(fundRepository.findById(userId, workFundId)).thenReturn(
            Fund(
                workFundId,
                userId,
                FundName("Work fund")
            )
        )
        whenever(fundRepository.findById(userId, expensesFundId)).thenReturn(
            Fund(
                expensesFundId,
                userId,
                FundName("Expenses Fund")
            )
        )
        whenever(transactionRepository.save(userId, request)).thenReturn(
            Transaction(
                id = transactionId,
                userId = userId,
                externalId = transactionExternalId,
                type = TransactionType.SINGLE_RECORD,
                dateTime = transactionTime,
                records = listOf(
                    TransactionRecord(
                        id = record1Id,
                        accountId = companyAccountId,
                        fundId = workFundId,
                        amount = BigDecimal("-100.25"),
                        unit = FinancialUnit.of("currency", "RON"),
                        labels = listOf(Label("one"), Label("two"))
                    ),
                    TransactionRecord(
                        id = record2Id,
                        accountId = personalAccountId,
                        fundId = expensesFundId,
                        amount = BigDecimal("100.25"),
                        unit = FinancialUnit.of("currency", "RON"),
                        labels = emptyList()
                    )
                )
            )
        )

        val transaction = transactionService.createTransaction(userId, request)

        assertThat(transaction.id).isEqualTo(transactionId)
        assertThat(transaction.userId).isEqualTo(userId)
        assertThat(transaction.dateTime).isEqualTo(transactionTime)
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
        val transactionTime = LocalDateTime.parse(rawTransactionTime)
        whenever(
            transactionRepository.list(userId, TransactionFilterTO.empty())
        ).thenReturn(
            listOf(
                Transaction(
                    id = transactionId,
                    userId = userId,
                    dateTime = transactionTime,
                    externalId = transactionExternalId,
                    type = TransactionType.SINGLE_RECORD,
                    records = listOf(
                        TransactionRecord(
                            id = record1Id,
                            accountId = companyAccountId,
                            fundId = workFundId,
                            amount = BigDecimal(100.25),
                            unit = FinancialUnit.of("currency", "RON"),
                            labels = listOf(Label("one"), Label("two"))
                        ),
                        TransactionRecord(
                            id = record2Id,
                            accountId = personalAccountId,
                            fundId = expensesFundId,
                            amount = BigDecimal(50.75),
                            unit = FinancialUnit.of("currency", "RON"),
                            labels = emptyList()
                        )
                    )
                )
            )
        )

        val transactions = transactionService.listTransactions(userId)

        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().id).isEqualTo(transactionId)
        assertThat(transactions.first().userId).isEqualTo(userId)
        assertThat(transactions.first().dateTime).isEqualTo(transactionTime)
        assertThat(transactions.first().records).hasSize(2)
        assertThat(transactions.first().records[0].id).isEqualTo(record1Id)
        assertThat(transactions.first().records[0].fundId).isEqualTo(workFundId)
        assertThat(transactions.first().records[0].accountId).isEqualTo(companyAccountId)
        assertThat(transactions.first().records[0].amount).isEqualTo(BigDecimal(100.25))
        assertThat(transactions.first().records[0].labels).containsExactlyInAnyOrder(Label("one"), Label("two"))
        assertThat(transactions.first().records[1].id).isEqualTo(record2Id)
        assertThat(transactions.first().records[1].fundId).isEqualTo(expensesFundId)
        assertThat(transactions.first().records[1].accountId).isEqualTo(personalAccountId)
        assertThat(transactions.first().records[1].amount).isEqualTo(BigDecimal(50.75))
        assertThat(transactions.first().records[1].labels).isEmpty()
    }

    @Test
    fun `given list transactions by fund id`(): Unit = runBlocking {
        val filter = TransactionFilterTO(fromDate = null, toDate = null, fundId = workFundId)
        whenever(transactionRepository.list(userId, filter)).thenReturn(
            listOf(
                Transaction(
                    id = transactionId,
                    userId = userId,
                    dateTime = transactionTime,
                    externalId = transactionExternalId,
                    type = TransactionType.TRANSFER,
                    records = listOf(
                        TransactionRecord(
                            id = record1Id,
                            accountId = companyAccountId,
                            fundId = workFundId,
                            amount = BigDecimal(100.25),
                            unit = FinancialUnit.of("currency", "RON"),
                            labels = listOf(Label("one"), Label("two"))
                        ),
                        TransactionRecord(
                            id = record2Id,
                            accountId = personalAccountId,
                            fundId = expensesFundId,
                            amount = BigDecimal(50.75),
                            unit = FinancialUnit.of("currency", "RON"),
                            labels = emptyList()
                        )
                    )
                )
            )
        )

        val transactions = transactionService.listTransactions(userId, TransactionFilterTO(fundId = workFundId))

        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().id).isEqualTo(transactionId)
        assertThat(transactions.first().userId).isEqualTo(userId)
        assertThat(transactions.first().dateTime).isEqualTo(transactionTime)
        assertThat(transactions.first().records).hasSize(2)
        assertThat(transactions.first().records[0].id).isEqualTo(record1Id)
        assertThat(transactions.first().records[0].fundId).isEqualTo(workFundId)
        assertThat(transactions.first().records[0].accountId).isEqualTo(companyAccountId)
        assertThat(transactions.first().records[0].amount).isEqualTo(BigDecimal(100.25))
        assertThat(transactions.first().records[0].labels).containsExactlyInAnyOrder(Label("one"), Label("two"))
        assertThat(transactions.first().records[1].id).isEqualTo(record2Id)
        assertThat(transactions.first().records[1].fundId).isEqualTo(expensesFundId)
        assertThat(transactions.first().records[1].accountId).isEqualTo(personalAccountId)
        assertThat(transactions.first().records[1].amount).isEqualTo(BigDecimal(50.75))
        assertThat(transactions.first().records[1].labels).isEmpty()
    }

    @Test
    fun `given delete transaction by id`(): Unit = runBlocking {
        val userId = randomUUID()
        val transactionId = randomUUID()
        transactionService.deleteTransaction(userId, transactionId)
        verify(transactionRepository).deleteById(userId, transactionId)
    }
}
