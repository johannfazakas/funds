package ro.jf.funds.account.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTransactionType
import ro.jf.funds.account.api.model.CreateAccountRecordTO
import ro.jf.funds.account.api.model.CreateAccountTransactionTO
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.service.domain.Account
import ro.jf.funds.account.service.domain.AccountServiceException
import ro.jf.funds.account.service.domain.AccountTransaction
import ro.jf.funds.account.service.persistence.AccountRepository
import ro.jf.funds.account.service.persistence.AccountTransactionRepository
import ro.jf.funds.commons.model.Currency
import java.math.BigDecimal
import java.util.UUID.randomUUID

class AccountTransactionServiceTest {
    private val accountTransactionRepository = mock<AccountTransactionRepository>()
    private val accountRepository = mock<AccountRepository>()
    private val accountTransactionService = AccountTransactionService(accountTransactionRepository, accountRepository)

    private val userId = randomUUID()

    private val cashAccount = Account(randomUUID(), userId, AccountName("Cash"), Currency.RON)
    private val incomeAccount = Account(randomUUID(), userId, AccountName("Income"), Currency.RON)

    private val dateTime1 = LocalDateTime.parse("2021-09-01T12:00:00")
    private val dateTime2 = LocalDateTime.parse("2022-10-01T12:00:00")

    @Test
    fun `given create single transaction`(): Unit = runBlocking {
        val recordRequest1 = CreateAccountRecordTO(cashAccount.id, BigDecimal("42.50"), Currency.RON)
        val recordRequest2 = CreateAccountRecordTO(incomeAccount.id, BigDecimal("-42.50"), Currency.RON)
        val transactionRequest = CreateAccountTransactionTO(
            dateTime1, "eid", AccountTransactionType.TRANSFER, listOf(recordRequest1, recordRequest2)
        )
        val expectedTransaction = mock<AccountTransaction>()

        whenever(accountRepository.findById(userId, cashAccount.id)).thenReturn(cashAccount)
        whenever(accountRepository.findById(userId, incomeAccount.id)).thenReturn(incomeAccount)
        whenever(accountTransactionRepository.save(userId, transactionRequest)).thenReturn(expectedTransaction)

        val createTransaction = accountTransactionService.createTransaction(userId, transactionRequest)

        assertThat(createTransaction).isEqualTo(expectedTransaction)
    }

    @Test
    fun `given create single transaction when currency mismatch should raise error`(): Unit = runBlocking {
        val recordRequest1 = CreateAccountRecordTO(cashAccount.id, BigDecimal("42.50"), Currency.RON)
        val recordRequest2 = CreateAccountRecordTO(cashAccount.id, BigDecimal("-42.50"), Currency.EUR)
        val transactionRequest = CreateAccountTransactionTO(
            dateTime1, "eid", AccountTransactionType.EXCHANGE, listOf(recordRequest1, recordRequest2)
        )
        val expectedTransaction = mock<AccountTransaction>()

        whenever(accountRepository.findById(userId, cashAccount.id)).thenReturn(cashAccount)
        whenever(accountTransactionRepository.save(userId, transactionRequest)).thenReturn(expectedTransaction)

        assertThatThrownBy { runBlocking { accountTransactionService.createTransaction(userId, transactionRequest) } }
            .isInstanceOf(AccountServiceException.AccountRecordCurrencyMismatch::class.java)
    }

    @Test
    fun `given create batch transactions`(): Unit = runBlocking {
        val recordRequest1 = CreateAccountRecordTO(cashAccount.id, BigDecimal("42.50"), Currency.RON)
        val recordRequest2 = CreateAccountRecordTO(incomeAccount.id, BigDecimal("-42.50"), Currency.RON)
        val recordRequest3 = CreateAccountRecordTO(cashAccount.id, BigDecimal("12.50"), Currency.RON)
        val transactionRequest1 = CreateAccountTransactionTO(
            dateTime1, "eid1", AccountTransactionType.SINGLE_RECORD, listOf(recordRequest1, recordRequest2)
        )
        val transactionRequest2 = CreateAccountTransactionTO(
            dateTime2, "eid2", AccountTransactionType.SINGLE_RECORD, listOf(recordRequest3)
        )
        val batchRequest = CreateAccountTransactionsTO(listOf(transactionRequest1, transactionRequest2))
        val expectedTransaction1 = mock<AccountTransaction>()
        val expectedTransaction2 = mock<AccountTransaction>()

        whenever(accountRepository.findById(userId, cashAccount.id)).thenReturn(cashAccount)
        whenever(accountRepository.findById(userId, incomeAccount.id)).thenReturn(incomeAccount)
        whenever(accountTransactionRepository.saveAll(userId, batchRequest))
            .thenReturn(listOf(expectedTransaction1, expectedTransaction2))

        val createTransaction = accountTransactionService.createTransactions(userId, batchRequest)

        assertThat(createTransaction).isEqualTo(listOf(expectedTransaction1, expectedTransaction2))
    }

    @Test
    fun `given create batch transactions when account not found should raise error`(): Unit = runBlocking {
        val recordRequest1 = CreateAccountRecordTO(cashAccount.id, BigDecimal("42.50"), Currency.RON)
        val recordRequest2 = CreateAccountRecordTO(incomeAccount.id, BigDecimal("-42.50"), Currency.RON)
        val recordRequest3 = CreateAccountRecordTO(cashAccount.id, BigDecimal("12.50"), Currency.RON)
        val transactionRequest1 = CreateAccountTransactionTO(
            dateTime1, "eid1", AccountTransactionType.SINGLE_RECORD, listOf(recordRequest1, recordRequest2)
        )
        val transactionRequest2 = CreateAccountTransactionTO(
            dateTime2, "eid2", AccountTransactionType.SINGLE_RECORD, listOf(recordRequest3)
        )
        val batchRequest = CreateAccountTransactionsTO(listOf(transactionRequest1, transactionRequest2))
        val expectedTransaction1 = mock<AccountTransaction>()
        val expectedTransaction2 = mock<AccountTransaction>()

        whenever(accountRepository.findById(userId, cashAccount.id)).thenReturn(cashAccount)
        whenever(accountRepository.findById(userId, incomeAccount.id)).thenReturn(null)
        whenever(accountTransactionRepository.saveAll(userId, batchRequest))
            .thenReturn(listOf(expectedTransaction1, expectedTransaction2))

        assertThatThrownBy { runBlocking { accountTransactionService.createTransactions(userId, batchRequest) } }
            .isInstanceOf(AccountServiceException.RecordAccountNotFound::class.java)
    }
}
