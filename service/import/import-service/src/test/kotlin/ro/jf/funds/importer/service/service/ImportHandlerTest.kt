package ro.jf.funds.importer.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.fund.sdk.FundTransactionSdk
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
        whenever(accountSdk.listAccounts(userId)).thenReturn(ListTO.of(cashAccount, bankAccount, companyAccount))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(expensedFund, incomeFund))

        importHandler.import(userId, importTransactions)

        verify(fundTransactionSdk).createTransactions(
            userId,
            CreateFundTransactionsTO(
                listOf(
                    CreateFundTransactionTO(
                        dateTime = transaction1DateTime,
                        records = listOf(
                            CreateFundRecordTO(
                                fundId = expensedFund.id,
                                accountId = bankAccount.id,
                                amount = BigDecimal("-100.00"),
                                unit = Currency.RON
                            )
                        )
                    ),
                    CreateFundTransactionTO(
                        dateTime = transaction2DateTime,
                        records = listOf(
                            CreateFundRecordTO(
                                fundId = incomeFund.id,
                                accountId = companyAccount.id,
                                amount = BigDecimal("-50.00"),
                                unit = Currency.RON
                            ),
                            CreateFundRecordTO(
                                fundId = expensedFund.id,
                                accountId = cashAccount.id,
                                amount = BigDecimal("50.00"),
                                unit = Currency.RON
                            ),
                        )
                    )
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
        whenever(accountSdk.listAccounts(userId)).thenReturn(ListTO.of(account("Cash RON")))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(fund("Expenses")))

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
        whenever(accountSdk.listAccounts(userId)).thenReturn(ListTO.of(account("Revolut")))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(fund("Investments")))

        assertThatThrownBy { runBlocking { importHandler.import(userId, importTransactions) } }
            .isInstanceOf(ImportDataException::class.java)
            .hasMessage("Record fund not found: Expenses")
    }

    private fun account(name: String): AccountTO =
        AccountTO(
            id = randomUUID(),
            name = AccountName(name),
            unit = Currency.RON
        )

    private fun fund(name: String): FundTO =
        FundTO(
            id = randomUUID(),
            name = FundName(name),
        )
}
