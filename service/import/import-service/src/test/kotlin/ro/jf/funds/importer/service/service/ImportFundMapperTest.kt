package ro.jf.funds.importer.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.fund.api.model.CreateFundRecordTO
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import java.math.BigDecimal
import java.util.UUID.randomUUID

class ImportFundMapperTest {
    private val accountSdk = mock<AccountSdk>()
    private val fundSdk = mock<FundSdk>()
    private val historicalPricingSdk = mock<HistoricalPricingSdk>()
    private val importFundMapper = ImportFundMapper(accountSdk, fundSdk, historicalPricingSdk)

    val userId = randomUUID()

    @Test
    fun `should map import transactions`(): Unit = runBlocking {
        val transaction1DateTime = LocalDateTime.parse("2024-07-22T09:17:00")
        val transaction2DateTime = LocalDateTime.parse("2024-07-22T09:18:00")
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionId = "transaction-1",
                dateTime = transaction1DateTime,
                records = listOf(
                    ImportParsedRecord(
                        AccountName("Revolut"),
                        FundName("Expenses"),
                        Currency.RON,
                        BigDecimal("-100.00")
                    )
                )
            ),
            ImportParsedTransaction(
                transactionId = "transaction-2",
                dateTime = transaction2DateTime,
                records = listOf(
                    ImportParsedRecord(AccountName("Company"), FundName("Income"), Currency.RON, BigDecimal("-50.00")),
                    ImportParsedRecord(AccountName("Cash RON"), FundName("Expenses"), Currency.RON, BigDecimal("50.00"))
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

        val fundTransactions = importFundMapper.mapToFundRequest(userId, importParsedTransactions)

        assertThat(fundTransactions.transactions).containsExactlyInAnyOrder(
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
    }

    @Test
    @Disabled("Not implemented yet")
    // TODO(Johann) make this green
    fun `should map exchange transaction`(): Unit = runBlocking {
        val dateTime = LocalDateTime(2019, 4, 23, 21, 45)
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionId = "transaction-1",
                dateTime = dateTime,
                records = listOf(
                    ImportParsedRecord(
                        AccountName("Cash EUR"),
                        FundName("Expenses"),
                        Currency.EUR,
                        "-1.89".toBigDecimal()
                    ),
                    ImportParsedRecord(
                        AccountName("Cash RON"),
                        FundName("Expenses"),
                        Currency.RON,
                        "-1434.00".toBigDecimal()
                    ),
                    ImportParsedRecord(
                        AccountName("Cash EUR"),
                        FundName("Expenses"),
                        Currency.EUR,
                        "301.24".toBigDecimal()
                    )
                )
            )
        )
        val eurAccount = account("Cash EUR", Currency.EUR)
        val ronAccount = account("Cash RON", Currency.RON)
        val expensedFund = fund("Expenses")

        whenever(accountSdk.listAccounts(userId)).thenReturn(ListTO.of(eurAccount, ronAccount))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(expensedFund))

        val fundTransactions = importFundMapper.mapToFundRequest(userId, importParsedTransactions)

        assertThat(fundTransactions.transactions).hasSize(1)
        assertThat(fundTransactions.transactions[0].dateTime).isEqualTo(dateTime)
        assertThat(fundTransactions.transactions[0].records).containsExactlyInAnyOrder(
            CreateFundRecordTO(
                fundId = expensedFund.id,
                accountId = eurAccount.id,
                amount = "-1.89".toBigDecimal(),
                unit = Currency.EUR
            ),
            CreateFundRecordTO(
                fundId = expensedFund.id,
                accountId = ronAccount.id,
                amount = "-1434.00".toBigDecimal(),
                unit = Currency.RON
            ),
            CreateFundRecordTO(
                fundId = expensedFund.id,
                accountId = eurAccount.id,
                amount = "301.24".toBigDecimal(),
                unit = Currency.EUR
            )
        )
    }

    @Test
    fun `should throw data exception when account not found`(): Unit = runBlocking {
        val userId = randomUUID()
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionId = "transaction-1",
                dateTime = LocalDateTime(2024, 7, 22, 9, 17),
                records = listOf(
                    ImportParsedRecord(
                        AccountName("Revolut"),
                        FundName("Expenses"),
                        Currency.RON,
                        BigDecimal("-100.00")
                    )
                )
            )
        )
        whenever(accountSdk.listAccounts(userId)).thenReturn(ListTO.of(account("Cash RON")))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(fund("Expenses")))

        assertThatThrownBy {
            runBlocking {
                importFundMapper.mapToFundRequest(
                    userId,
                    importParsedTransactions
                )
            }
        }
            .isInstanceOf(ImportDataException::class.java)
            .hasMessage("Record account not found: Revolut")
    }

    @Test
    fun `should throw data exception when fund not found`(): Unit = runBlocking {
        val userId = randomUUID()
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionId = "transaction-1",
                dateTime = LocalDateTime(2024, 7, 22, 9, 17),
                records = listOf(
                    ImportParsedRecord(
                        AccountName("Revolut"),
                        FundName("Expenses"),
                        Currency.RON,
                        BigDecimal("-100.00")
                    )
                )
            )
        )
        whenever(accountSdk.listAccounts(userId)).thenReturn(ListTO.of(account("Revolut")))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(fund("Investments")))

        assertThatThrownBy {
            runBlocking {
                importFundMapper.mapToFundRequest(
                    userId,
                    importParsedTransactions
                )
            }
        }
            .isInstanceOf(ImportDataException::class.java)
            .hasMessage("Record fund not found: Expenses")
    }

    private fun account(name: String, currency: Currency = Currency.RON): AccountTO =
        AccountTO(
            id = randomUUID(),
            name = AccountName(name),
            unit = currency
        )

    private fun fund(name: String): FundTO =
        FundTO(
            id = randomUUID(),
            name = FundName(name),
        )
}
