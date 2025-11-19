package ro.jf.funds.fund.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ro.jf.funds.commons.api.model.FinancialUnit
import ro.jf.funds.commons.api.model.Label
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
        val request = CreateTransactionTO.Transfer(
            dateTime = transactionTime,
            externalId = transactionExternalId,
            sourceRecord = CreateTransactionRecordTO(
                accountId = companyAccountId,
                fundId = workFundId,
                amount = BigDecimal("-100.25").toDouble(),
                unit = FinancialUnit.of("currency", "RON"),
                labels = listOf(Label("one"), Label("two"))
            ),
            destinationRecord = CreateTransactionRecordTO(
                accountId = personalAccountId,
                fundId = expensesFundId,
                amount = BigDecimal("100.25").toDouble(),
                unit = FinancialUnit.of("currency", "RON")
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
            Transaction.Transfer(
                id = transactionId,
                userId = userId,
                externalId = transactionExternalId,
                dateTime = transactionTime,
                sourceRecord = TransactionRecord(
                    id = record1Id,
                    accountId = companyAccountId,
                    fundId = workFundId,
                    amount = BigDecimal("-100.25"),
                    unit = FinancialUnit.of("currency", "RON"),
                    labels = listOf(Label("one"), Label("two"))
                ),
                destinationRecord = TransactionRecord(
                    id = record2Id,
                    accountId = personalAccountId,
                    fundId = expensesFundId,
                    amount = BigDecimal("100.25"),
                    unit = FinancialUnit.of("currency", "RON"),
                    labels = emptyList()
                )
            )
        )

        val transaction = transactionService.createTransaction(userId, request) as Transaction.Transfer

        assertThat(transaction.id).isEqualTo(transactionId)
        assertThat(transaction.userId).isEqualTo(userId)
        assertThat(transaction.dateTime).isEqualTo(transactionTime)
        assertThat(transaction.sourceRecord.id).isEqualTo(record1Id)
        assertThat(transaction.sourceRecord.fundId).isEqualTo(workFundId)
        assertThat(transaction.sourceRecord.accountId).isEqualTo(companyAccountId)
        assertThat(transaction.sourceRecord.amount).isEqualTo(BigDecimal("-100.25"))
        assertThat(transaction.destinationRecord.id).isEqualTo(record2Id)
        assertThat(transaction.destinationRecord.fundId).isEqualTo(expensesFundId)
        assertThat(transaction.destinationRecord.accountId).isEqualTo(personalAccountId)
        assertThat(transaction.destinationRecord.amount).isEqualTo(BigDecimal("100.25"))
    }

    @Test
    fun `given list transactions`(): Unit = runBlocking {
        val transactionTime = LocalDateTime.parse(rawTransactionTime)
        whenever(
            transactionRepository.list(userId, TransactionFilterTO.empty())
        ).thenReturn(
            listOf(
                Transaction.Transfer(
                    id = transactionId,
                    userId = userId,
                    dateTime = transactionTime,
                    externalId = transactionExternalId,
                    sourceRecord = TransactionRecord(
                        id = record1Id,
                        accountId = companyAccountId,
                        fundId = workFundId,
                        amount = BigDecimal(100.25),
                        unit = FinancialUnit.of("currency", "RON"),
                        labels = listOf(Label("one"), Label("two"))
                    ),
                    destinationRecord = TransactionRecord(
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

        val transactions = transactionService.listTransactions(userId)

        assertThat(transactions).hasSize(1)
        val transaction = transactions.first() as Transaction.Transfer
        assertThat(transaction.id).isEqualTo(transactionId)
        assertThat(transaction.userId).isEqualTo(userId)
        assertThat(transaction.dateTime).isEqualTo(transactionTime)
        assertThat(transaction.sourceRecord.id).isEqualTo(record1Id)
        assertThat(transaction.sourceRecord.fundId).isEqualTo(workFundId)
        assertThat(transaction.sourceRecord.accountId).isEqualTo(companyAccountId)
        assertThat(transaction.sourceRecord.amount).isEqualTo(BigDecimal(100.25))
        assertThat(transaction.sourceRecord.labels).containsExactlyInAnyOrder(Label("one"), Label("two"))
        assertThat(transaction.destinationRecord.id).isEqualTo(record2Id)
        assertThat(transaction.destinationRecord.fundId).isEqualTo(expensesFundId)
        assertThat(transaction.destinationRecord.accountId).isEqualTo(personalAccountId)
        assertThat(transaction.destinationRecord.amount).isEqualTo(BigDecimal(50.75))
        assertThat(transaction.destinationRecord.labels).isEmpty()
    }

    @Test
    fun `given list transactions by fund id`(): Unit = runBlocking {
        val filter = TransactionFilterTO(fromDate = null, toDate = null, fundId = workFundId)
        whenever(transactionRepository.list(userId, filter)).thenReturn(
            listOf(
                Transaction.Transfer(
                    id = transactionId,
                    userId = userId,
                    dateTime = transactionTime,
                    externalId = transactionExternalId,
                    sourceRecord = TransactionRecord(
                        id = record1Id,
                        accountId = companyAccountId,
                        fundId = workFundId,
                        amount = BigDecimal(100.25),
                        unit = FinancialUnit.of("currency", "RON"),
                        labels = listOf(Label("one"), Label("two"))
                    ),
                    destinationRecord = TransactionRecord(
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

        val transactions = transactionService.listTransactions(userId, TransactionFilterTO(fundId = workFundId))

        assertThat(transactions).hasSize(1)
        val transaction = transactions.first() as Transaction.Transfer
        assertThat(transaction.id).isEqualTo(transactionId)
        assertThat(transaction.userId).isEqualTo(userId)
        assertThat(transaction.dateTime).isEqualTo(transactionTime)
        assertThat(transaction.sourceRecord.id).isEqualTo(record1Id)
        assertThat(transaction.sourceRecord.fundId).isEqualTo(workFundId)
        assertThat(transaction.sourceRecord.accountId).isEqualTo(companyAccountId)
        assertThat(transaction.sourceRecord.amount).isEqualTo(BigDecimal(100.25))
        assertThat(transaction.sourceRecord.labels).containsExactlyInAnyOrder(Label("one"), Label("two"))
        assertThat(transaction.destinationRecord.id).isEqualTo(record2Id)
        assertThat(transaction.destinationRecord.fundId).isEqualTo(expensesFundId)
        assertThat(transaction.destinationRecord.accountId).isEqualTo(personalAccountId)
        assertThat(transaction.destinationRecord.amount).isEqualTo(BigDecimal(50.75))
        assertThat(transaction.destinationRecord.labels).isEmpty()
    }

    @Test
    fun `given delete transaction by id`(): Unit = runBlocking {
        val userId = randomUUID()
        val transactionId = randomUUID()
        transactionService.deleteTransaction(userId, transactionId)
        verify(transactionRepository).deleteById(userId, transactionId)
    }
}
