package ro.jf.funds.importer.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import ro.jf.bk.account.api.model.AccountName
import ro.jf.bk.account.api.model.AccountTO
import ro.jf.bk.account.sdk.AccountSdk
import ro.jf.bk.fund.api.model.FundName
import ro.jf.bk.fund.api.model.FundTO
import ro.jf.bk.fund.sdk.FundSdk
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.domain.ImportRecord
import ro.jf.funds.importer.service.domain.ImportTransaction
import java.math.BigDecimal
import java.util.UUID.randomUUID

class ImportHandlerTest {
    private val accountSdk = mock<AccountSdk>()
    private val fundSdk = mock<FundSdk>()
    private val importHandler = ImportHandler(accountSdk, fundSdk)

    @Test
    fun `should handle simple import transactions`(): Unit = runBlocking {
        val userId = randomUUID()
        val importTransactions = listOf(
            ImportTransaction(
                transactionId = "transaction-1",
                dateTime = LocalDateTime(2024, 7, 22, 9, 17),
                records = listOf(
                    ImportRecord(AccountName("Revolut"), FundName("Expenses"), "RON", BigDecimal("-100.00"))
                )
            ),
            ImportTransaction(
                transactionId = "transaction-2",
                dateTime = LocalDateTime(2024, 7, 22, 9, 18),
                records = listOf(
                    ImportRecord(AccountName("Cash RON"), FundName("Expenses"), "RON", BigDecimal("-50.00"))
                )
            )
        )
        whenever(accountSdk.listAccounts(userId))
            .thenReturn(listOf(account("Cash RON"), account("Revolut")))
        whenever(fundSdk.listFunds(userId)).thenReturn(listOf(fund("Expenses")))

        importHandler.import(userId, importTransactions)

        // TODO(Johann) verify side effects
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
