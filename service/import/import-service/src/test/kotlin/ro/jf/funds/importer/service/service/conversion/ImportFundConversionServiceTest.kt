package ro.jf.funds.importer.service.service.conversion

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.fund.api.model.CreateFundRecordTO
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.service.conversion.strategy.ExchangeSingleFundConverter
import ro.jf.funds.importer.service.service.conversion.strategy.ImplicitTransferFundConverter
import ro.jf.funds.importer.service.service.conversion.strategy.ImportFundConverterRegistry
import ro.jf.funds.importer.service.service.conversion.strategy.SingleRecordFundConverter
import ro.jf.funds.importer.service.service.conversion.strategy.TransferFundConverter
import java.math.BigDecimal
import java.util.*
import java.util.UUID.randomUUID

class ImportFundConversionServiceTest {
    private val accountSdk = mock<AccountSdk>()
    private val accountService = AccountService(accountSdk)
    private val fundSdk = mock<FundSdk>()
    private val fundService = FundService(fundSdk)
    private val historicalPricingSdk = mock<HistoricalPricingSdk>()
    private val importFundConverterRegistry = ImportFundConverterRegistry(
        listOf(
            SingleRecordFundConverter(),
            TransferFundConverter(),
            ImplicitTransferFundConverter(),
            ExchangeSingleFundConverter()
        )
    )
    private val importFundConversionService =
        ImportFundConversionService(accountService, fundService, importFundConverterRegistry, historicalPricingSdk)

    private val userId: UUID = randomUUID()

    @Test
    fun `should map single record import transactions`(): Unit = runBlocking {
        val transactionDateTime = LocalDateTime.parse("2024-07-22T09:17:00")
        val transactionExternalId = "transaction-1"
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionExternalId = transactionExternalId,
                dateTime = transactionDateTime,
                records = listOf(
                    ImportParsedRecord(
                        AccountName("Revolut"),
                        FundName("Expenses"),
                        Currency.RON,
                        BigDecimal("-100.00"),
                        listOf(Label("one"), Label("two"))
                    )
                )
            )
        )
        val bankAccount = account("Revolut")
        val expensedFund = fund("Expenses")
        whenever(accountSdk.listAccounts(userId)).thenReturn(ListTO.of(bankAccount))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(expensedFund))
        whenever(historicalPricingSdk.convert(userId, ConversionsRequest(emptyList())))
            .thenReturn(ConversionsResponse.empty())

        val fundTransactions = importFundConversionService.mapToFundRequest(userId, importParsedTransactions)

        assertThat(fundTransactions.transactions).containsExactlyInAnyOrder(
            CreateFundTransactionTO(
                dateTime = transactionDateTime,
                externalId = transactionExternalId,
                records = listOf(
                    CreateFundRecordTO(
                        fundId = expensedFund.id,
                        accountId = bankAccount.id,
                        amount = BigDecimal("-100.00"),
                        unit = Currency.RON,
                        labels = listOf(Label("one"), Label("two"))
                    )
                )
            )
        )
    }

    @Test
    fun `should map single record import transactions with currency conversion`(): Unit = runBlocking {
        val transactionDateTime = LocalDateTime.parse("2024-07-22T09:17:00")
        val transactionDate = transactionDateTime.date
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionExternalId = "transaction-1",
                dateTime = transactionDateTime,
                records = listOf(
                    ImportParsedRecord(
                        AccountName("Revolut"),
                        FundName("Expenses"),
                        Currency.RON,
                        BigDecimal("-100.00"),
                        listOf(Label("one"), Label("two"))
                    )
                )
            )
        )
        val bankAccount = account("Revolut", Currency.EUR)
        val expensedFund = fund("Expenses")
        whenever(accountSdk.listAccounts(userId)).thenReturn(ListTO.of(bankAccount))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(expensedFund))
        val conversionRequest = ConversionRequest(Currency.RON, Currency.EUR, transactionDate)
        val conversionResponse = ConversionResponse(Currency.RON, Currency.EUR, transactionDate, BigDecimal("0.2"))
        whenever(historicalPricingSdk.convert(userId, ConversionsRequest(listOf(conversionRequest))))
            .thenReturn(ConversionsResponse(listOf(conversionResponse)))

        val fundTransactions = importFundConversionService.mapToFundRequest(userId, importParsedTransactions)

        assertThat(fundTransactions.transactions).hasSize(1)
        assertThat(fundTransactions.transactions[0].dateTime).isEqualTo(transactionDateTime)
        assertThat(fundTransactions.transactions[0].records).hasSize(1)

        val record = fundTransactions.transactions[0].records.first()
        assertThat(record.amount).isEqualByComparingTo(BigDecimal("-20.00"))
        assertThat(record.unit).isEqualTo(Currency.EUR)
        assertThat(record.labels).containsExactly(Label("one"), Label("two"))
    }

    @Test
    fun `should map transfer import transactions`(): Unit = runBlocking {
        val transactionDateTime = LocalDateTime.parse("2024-07-22T09:18:00")
        val transactionExternalId = "transaction-2"
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionExternalId = transactionExternalId,
                dateTime = transactionDateTime,
                records = listOf(
                    ImportParsedRecord(
                        AccountName("Company"),
                        FundName("Income"),
                        Currency.RON,
                        BigDecimal("-50.00"),
                        listOf(Label("Basic"))
                    ),
                    ImportParsedRecord(
                        AccountName("Cash RON"),
                        FundName("Expenses"),
                        Currency.RON,
                        BigDecimal("50.00"),
                        listOf(Label("Basic"))
                    )
                )
            )
        )
        val cashAccount = account("Cash RON")
        val companyAccount = account("Company")
        val expensedFund = fund("Expenses")
        val incomeFund = fund("Income")
        whenever(accountSdk.listAccounts(userId)).thenReturn(ListTO.of(cashAccount, companyAccount))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(expensedFund, incomeFund))
        whenever(historicalPricingSdk.convert(userId, ConversionsRequest(emptyList())))
            .thenReturn(ConversionsResponse.empty())

        val fundTransactions = importFundConversionService.mapToFundRequest(userId, importParsedTransactions)

        assertThat(fundTransactions.transactions).containsExactlyInAnyOrder(
            CreateFundTransactionTO(
                dateTime = transactionDateTime,
                externalId = transactionExternalId,
                records = listOf(
                    CreateFundRecordTO(
                        fundId = incomeFund.id,
                        accountId = companyAccount.id,
                        amount = BigDecimal("-50.00"),
                        unit = Currency.RON,
                        labels = listOf(Label("Basic"))
                    ),
                    CreateFundRecordTO(
                        fundId = expensedFund.id,
                        accountId = cashAccount.id,
                        amount = BigDecimal("50.00"),
                        unit = Currency.RON,
                        labels = listOf(Label("Basic"))
                    ),
                )
            )
        )
    }

    @Test
    fun `should map transfer import transactions with currency conversion`(): Unit = runBlocking {
        val transactionDateTime = LocalDateTime.parse("2024-07-22T09:18:00")
        val transactionDate = transactionDateTime.date
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionExternalId = "transaction-2",
                dateTime = transactionDateTime,
                records = listOf(
                    ImportParsedRecord(
                        AccountName("Company"),
                        FundName("Income"),
                        Currency.RON,
                        BigDecimal("-50.00"),
                        listOf(Label("work_income"))
                    ),
                    ImportParsedRecord(
                        AccountName("Cash RON"),
                        FundName("Expenses"),
                        Currency.RON,
                        BigDecimal("50.00"),
                        listOf(Label("basic"))
                    )
                )
            )
        )
        val cashAccount = account("Cash RON", Currency.EUR)
        val companyAccount = account("Company", Currency.EUR)
        val expensedFund = fund("Expenses")
        val incomeFund = fund("Income")
        whenever(accountSdk.listAccounts(userId)).thenReturn(ListTO.of(cashAccount, companyAccount))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(expensedFund, incomeFund))

        val conversionRequest = ConversionRequest(Currency.RON, Currency.EUR, transactionDate)
        val conversionResponse = ConversionResponse(Currency.RON, Currency.EUR, transactionDate, BigDecimal("0.20"))
        whenever(historicalPricingSdk.convert(userId, ConversionsRequest(listOf(conversionRequest))))
            .thenReturn(ConversionsResponse(listOf(conversionResponse)))

        val fundTransactions = importFundConversionService.mapToFundRequest(userId, importParsedTransactions)

        assertThat(fundTransactions.transactions).hasSize(1)
        assertThat(fundTransactions.transactions[0].dateTime).isEqualTo(transactionDateTime)
        assertThat(fundTransactions.transactions[0].records).hasSize(2)

        val companyRecord = fundTransactions.transactions[0].records.first { it.accountId == companyAccount.id }
        val cashRecord = fundTransactions.transactions[0].records.first { it.accountId == cashAccount.id }

        assertThat(companyRecord.amount).isEqualByComparingTo(BigDecimal("-10.00"))
        assertThat(companyRecord.unit).isEqualTo(Currency.EUR)
        assertThat(companyRecord.labels).containsExactly(Label("work_income"))
        assertThat(cashRecord.amount).isEqualByComparingTo(BigDecimal("10.00"))
        assertThat(cashRecord.unit).isEqualTo(Currency.EUR)
        assertThat(cashRecord.labels).containsExactly(Label("basic"))
    }

    @Test
    fun `should map transaction with implicit transfer`(): Unit = runBlocking {
        val transactionDateTime = LocalDateTime.parse("2024-07-22T09:18:00")
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionExternalId = "transaction-id",
                dateTime = transactionDateTime,
                records = listOf(
                    ImportParsedRecord(
                        AccountName("BT RON"),
                        FundName("Income"),
                        Currency.RON,
                        BigDecimal("50.00"),
                        listOf(Label("income"))
                    ),
                    ImportParsedRecord(
                        AccountName("BT RON"), FundName("Income"), Currency.RON, BigDecimal("-50.00"), emptyList()
                    ),
                    ImportParsedRecord(
                        AccountName("BT RON"), FundName("Expenses"), Currency.RON, BigDecimal("50.00"), emptyList()
                    ),
                )
            )
        )
        val account = account("BT RON", Currency.RON)
        val expensedFund = fund("Expenses")
        val incomeFund = fund("Income")
        whenever(accountSdk.listAccounts(userId)).thenReturn(ListTO.of(account))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(expensedFund, incomeFund))
        whenever(historicalPricingSdk.convert(userId, ConversionsRequest(emptyList())))
            .thenReturn(ConversionsResponse.empty())

        val fundTransactions = importFundConversionService.mapToFundRequest(userId, importParsedTransactions)

        assertThat(fundTransactions.transactions).hasSize(1)
        assertThat(fundTransactions.transactions[0].dateTime).isEqualTo(transactionDateTime)
        val records = fundTransactions.transactions[0].records
        assertThat(records).hasSize(3)

        val passThroughPositiveRecord =
            records.first { it.fundId == incomeFund.id && it.amount > BigDecimal.ZERO }
        assertThat(passThroughPositiveRecord.amount).isEqualByComparingTo(BigDecimal("50.00"))
        assertThat(passThroughPositiveRecord.unit).isEqualTo(Currency.RON)
        assertThat(passThroughPositiveRecord.labels).containsExactly(Label("income"))

        val passThroughNegativeRecord =
            records.first { it.fundId == incomeFund.id && it.amount < BigDecimal.ZERO }
        assertThat(passThroughNegativeRecord.amount).isEqualByComparingTo(BigDecimal("-50.00"))
        assertThat(passThroughNegativeRecord.unit).isEqualTo(Currency.RON)
        assertThat(passThroughNegativeRecord.labels).isEmpty()

        val targetRecord = records
            .first { it.fundId == expensedFund.id && it.accountId == account.id }
        assertThat(targetRecord.amount).isEqualByComparingTo(BigDecimal("50.00"))
        assertThat(targetRecord.unit).isEqualTo(Currency.RON)
        assertThat(targetRecord.labels).isEmpty()
    }

    @Test
    fun `should map exchange transaction`(): Unit = runBlocking {
        val dateTime = LocalDateTime.parse("2019-04-01T09:18:00")
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionExternalId = "transaction-1",
                dateTime = dateTime,
                records = listOf(
                    ImportParsedRecord(
                        AccountName("Cash EUR"),
                        FundName("Expenses"),
                        Currency.EUR,
                        "-1.89".toBigDecimal(),
                        listOf(Label("exchange"), Label("finance"))
                    ),
                    ImportParsedRecord(
                        AccountName("Cash RON"),
                        FundName("Expenses"),
                        Currency.RON,
                        "-1434.00".toBigDecimal(),
                        listOf(Label("exchange"))
                    ),
                    ImportParsedRecord(
                        AccountName("Cash EUR"),
                        FundName("Expenses"),
                        Currency.EUR,
                        "301.24".toBigDecimal(),
                        listOf(Label("exchange"))
                    )
                )
            )
        )
        val eurAccount = account("Cash EUR", Currency.EUR)
        val ronAccount = account("Cash RON", Currency.RON)
        val expenses = fund("Expenses")

        whenever(accountSdk.listAccounts(userId)).thenReturn(ListTO.of(eurAccount, ronAccount))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(expenses))

        val conversionsRequest = ConversionsRequest(
            listOf(
                ConversionRequest(Currency.RON, Currency.EUR, dateTime.date),
                ConversionRequest(Currency.EUR, Currency.RON, dateTime.date),
            )
        )
        val conversionsResponse = ConversionsResponse(
            listOf(
                ConversionResponse(Currency.RON, Currency.EUR, dateTime.date, BigDecimal("0.20998")),
                ConversionResponse(Currency.EUR, Currency.RON, dateTime.date, BigDecimal("4.76235")),
            )
        )
        whenever(historicalPricingSdk.convert(userId, conversionsRequest)).thenReturn(conversionsResponse)

        val fundTransactions = importFundConversionService.mapToFundRequest(userId, importParsedTransactions)

        assertThat(fundTransactions.transactions).hasSize(1)
        assertThat(fundTransactions.transactions[0].dateTime).isEqualTo(dateTime)
        val records = fundTransactions.transactions[0].records
        assertThat(records).hasSize(3)

        assertThat(records[0].amount).isEqualByComparingTo("301.24".toBigDecimal())
        assertThat(records[0].accountId).isEqualTo(eurAccount.id)
        assertThat(records[0].fundId).isEqualTo(expenses.id)
        assertThat(records[0].unit).isEqualTo(Currency.EUR)
        assertThat(records[0].labels).containsExactlyInAnyOrder(Label("exchange"))

        assertThat(records[1].amount).isEqualByComparingTo("-1434.6103140".toBigDecimal())
        assertThat(records[1].accountId).isEqualTo(ronAccount.id)
        assertThat(records[1].fundId).isEqualTo(expenses.id)
        assertThat(records[1].unit).isEqualTo(Currency.RON)
        assertThat(records[1].labels).containsExactlyInAnyOrder(Label("exchange"))

        assertThat(records[2].amount).isEqualByComparingTo("0.6103140".toBigDecimal())
        assertThat(records[2].accountId).isEqualTo(ronAccount.id)
        assertThat(records[2].fundId).isEqualTo(expenses.id)
        assertThat(records[2].unit).isEqualTo(Currency.RON)
        assertThat(records[2].labels).containsExactlyInAnyOrder(Label("exchange"), Label("finance"))
    }

    @Test
    fun `should throw data exception when account not found`(): Unit = runBlocking {
        val userId = randomUUID()
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionExternalId = "transaction-1",
                dateTime = LocalDateTime(2024, 7, 22, 9, 17),
                records = listOf(
                    ImportParsedRecord(
                        AccountName("Revolut"),
                        FundName("Expenses"),
                        Currency.RON,
                        BigDecimal("-100.00"),
                        listOf(Label("basic"))
                    )
                )
            )
        )
        whenever(accountSdk.listAccounts(userId)).thenReturn(ListTO.of(account("Cash RON")))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(fund("Expenses")))

        assertThatThrownBy {
            runBlocking {
                importFundConversionService.mapToFundRequest(
                    userId,
                    importParsedTransactions
                )
            }
        }
            .isInstanceOf(ImportDataException::class.java)
    }

    @Test
    fun `should throw data exception when fund not found`(): Unit = runBlocking {
        val userId = randomUUID()
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionExternalId = "transaction-1",
                dateTime = LocalDateTime(2024, 7, 22, 9, 17),
                records = listOf(
                    ImportParsedRecord(
                        AccountName("Revolut"),
                        FundName("Expenses"),
                        Currency.RON,
                        BigDecimal("-100.00"),
                        listOf(Label("basic"))
                    )
                )
            )
        )
        whenever(accountSdk.listAccounts(userId)).thenReturn(ListTO.of(account("Revolut")))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(fund("Investments")))
        whenever(historicalPricingSdk.convert(userId, ConversionsRequest(emptyList())))
            .thenReturn(ConversionsResponse.empty())

        assertThatThrownBy {
            runBlocking {
                importFundConversionService.mapToFundRequest(
                    userId,
                    importParsedTransactions
                )
            }
        }
            .isInstanceOf(ImportDataException::class.java)
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
