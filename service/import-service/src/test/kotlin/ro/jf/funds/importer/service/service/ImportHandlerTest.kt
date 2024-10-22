package ro.jf.funds.importer.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ro.jf.bk.account.api.model.AccountName
import ro.jf.bk.account.api.model.AccountTO
import ro.jf.bk.account.sdk.AccountSdk
import ro.jf.bk.fund.api.model.CreateFundRecordTO
import ro.jf.bk.fund.api.model.CreateFundTransactionTO
import ro.jf.bk.fund.api.model.FundName
import ro.jf.bk.fund.api.model.FundTO
import ro.jf.bk.fund.sdk.FundSdk
import ro.jf.bk.fund.sdk.FundTransactionSdk
import ro.jf.funds.importer.service.domain.ImportRecord
import ro.jf.funds.importer.service.domain.ImportTransaction
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import java.math.BigDecimal
import java.util.UUID.randomUUID

class ImportHandlerTest {
    private val accountSdk = mock<AccountSdk>()
    private val fundSdk = mock<FundSdk>()
    private val fundTransactionSdk = mock<FundTransactionSdk>()
    private val importHandler = ImportHandler(accountSdk, fundSdk, fundTransactionSdk)

    @Test
    fun `should handle import transactions`(): Unit = runBlocking {
        val userId = randomUUID()
        val transaction1DateTime = LocalDateTime.parse("2024-07-22T09:17:00")
        val transaction2DateTime = LocalDateTime.parse("2024-07-22T09:18:00")
        val importTransactions = listOf(
            ImportTransaction(
                transactionId = "transaction-1",
                dateTime = transaction1DateTime,
                records = listOf(
                    ImportRecord(AccountName("Revolut"), FundName("Expenses"), "RON", BigDecimal("-100.00"))
                )
            ),
            ImportTransaction(
                transactionId = "transaction-2",
                dateTime = transaction2DateTime,
                records = listOf(
                    ImportRecord(AccountName("Company"), FundName("Income"), "RON", BigDecimal("-50.00")),
                    ImportRecord(AccountName("Cash RON"), FundName("Expenses"), "RON", BigDecimal("50.00"))
                )
            )
        )
        val cashAccount = account("Cash RON")
        val bankAccount = account("Revolut")
        val companyAccount = account("Company")
        val expensedFund = fund("Expenses")
        val incomeFund = fund("Income")
        whenever(accountSdk.listAccounts(userId)).thenReturn(listOf(cashAccount, bankAccount, companyAccount))
        whenever(fundSdk.listFunds(userId)).thenReturn(listOf(expensedFund, incomeFund))

        importHandler.import(userId, importTransactions)

        verify(fundTransactionSdk).createTransaction(
            userId,
            CreateFundTransactionTO(
                dateTime = transaction1DateTime,
                records = listOf(
                    CreateFundRecordTO(
                        fundId = expensedFund.id,
                        accountId = bankAccount.id,
                        amount = BigDecimal("-100.00")
                    )
                )
            )
        )
        verify(fundTransactionSdk).createTransaction(
            userId,
            CreateFundTransactionTO(
                dateTime = transaction2DateTime,
                records = listOf(
                    CreateFundRecordTO(
                        fundId = incomeFund.id,
                        accountId = companyAccount.id,
                        amount = BigDecimal("-50.00")
                    ),
                    CreateFundRecordTO(
                        fundId = expensedFund.id,
                        accountId = cashAccount.id,
                        amount = BigDecimal("50.00")
                    ),
                )
            )
        )
    }

    @Test
    fun `should throw data exception when account not found`(): Unit = runBlocking {
        val userId = randomUUID()
        val importTransactions = listOf(
            ImportTransaction(
                transactionId = "transaction-1",
                dateTime = LocalDateTime(2024, 7, 22, 9, 17),
                records = listOf(
                    ImportRecord(AccountName("Revolut"), FundName("Expenses"), "RON", BigDecimal("-100.00"))
                )
            )
        )
        whenever(accountSdk.listAccounts(userId)).thenReturn(listOf(account("Cash RON")))
        whenever(fundSdk.listFunds(userId)).thenReturn(listOf(fund("Expenses")))

        assertThatThrownBy { runBlocking { importHandler.import(userId, importTransactions) } }
            .isInstanceOf(ImportDataException::class.java)
            .hasMessage("Record account not found: Revolut")
    }

    @Test
    fun `should throw data exception when fund not found`(): Unit = runBlocking {
        val userId = randomUUID()
        val importTransactions = listOf(
            ImportTransaction(
                transactionId = "transaction-1",
                dateTime = LocalDateTime(2024, 7, 22, 9, 17),
                records = listOf(
                    ImportRecord(AccountName("Revolut"), FundName("Expenses"), "RON", BigDecimal("-100.00"))
                )
            )
        )
        whenever(accountSdk.listAccounts(userId)).thenReturn(listOf(account("Revolut")))
        whenever(fundSdk.listFunds(userId)).thenReturn(listOf(fund("Investments")))

        assertThatThrownBy { runBlocking { importHandler.import(userId, importTransactions) } }
            .isInstanceOf(ImportDataException::class.java)
            .hasMessage("Record fund not found: Expenses")
    }

    private fun account(name: String): AccountTO.Currency =
        AccountTO.Currency(
            id = randomUUID(),
            name = AccountName(name),
            currency = "RON"
        )

    private fun fund(name: String): FundTO =
        FundTO(
            id = randomUUID(),
            name = FundName(name),
            accounts = emptyList()
        )
}
