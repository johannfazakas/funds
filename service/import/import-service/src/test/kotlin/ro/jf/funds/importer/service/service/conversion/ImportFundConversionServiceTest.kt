package ro.jf.funds.importer.service.service.conversion

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.sdk.FundAccountSdk
import ro.jf.funds.commons.model.*
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.service.conversion.strategy.*
import java.math.BigDecimal
import java.util.*
import java.util.UUID.randomUUID

class ImportFundConversionServiceTest {
    private val fundAccountSdk = mock<FundAccountSdk>()
    private val accountService = AccountService(fundAccountSdk)
    private val fundSdk = mock<FundSdk>()
    private val fundService = FundService(fundSdk)
    private val historicalPricingSdk = mock<HistoricalPricingSdk>()
    private val importTransactionConverterRegistry = ImportTransactionConverterRegistry(
        listOf(
            SingleRecordTransactionConverter(),
            TransferTransactionConverter(),
            ExchangeSingleTransactionConverter(),
            InvestmentTransactionConverter()
        )
    )
    private val importFundConversionService =
        ImportFundConversionService(
            accountService,
            fundService,
            importTransactionConverterRegistry,
            historicalPricingSdk,
        )

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
        whenever(fundAccountSdk.listAccounts(userId)).thenReturn(ListTO.of(bankAccount))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(expensedFund))
        whenever(historicalPricingSdk.convert(userId, ConversionsRequest(emptyList())))
            .thenReturn(ConversionsResponse.empty())

        val fundTransactions = importFundConversionService.mapToFundRequest(userId, importParsedTransactions)

        assertThat(fundTransactions.transactions).containsExactlyInAnyOrder(
            CreateFundTransactionTO(
                dateTime = transactionDateTime,
                externalId = transactionExternalId,
                type = FundTransactionType.SINGLE_RECORD,
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
        whenever(fundAccountSdk.listAccounts(userId)).thenReturn(ListTO.of(bankAccount))
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
        whenever(fundAccountSdk.listAccounts(userId)).thenReturn(ListTO.of(cashAccount, companyAccount))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(expensedFund, incomeFund))
        whenever(historicalPricingSdk.convert(userId, ConversionsRequest(emptyList())))
            .thenReturn(ConversionsResponse.empty())

        val fundTransactions = importFundConversionService.mapToFundRequest(userId, importParsedTransactions)

        assertThat(fundTransactions.transactions).containsExactlyInAnyOrder(
            CreateFundTransactionTO(
                dateTime = transactionDateTime,
                externalId = transactionExternalId,
                type = FundTransactionType.TRANSFER,
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
        whenever(fundAccountSdk.listAccounts(userId)).thenReturn(ListTO.of(cashAccount, companyAccount))
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
    fun `should map implicit transfer transaction`(): Unit = runBlocking {
        val transactionDateTime = LocalDateTime.parse("2024-07-22T09:18:00")
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionExternalId = "transaction-id",
                dateTime = transactionDateTime,
                records = listOf(
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
        val expenseFund = fund("Expenses")
        val incomeFund = fund("Income")
        whenever(fundAccountSdk.listAccounts(userId)).thenReturn(ListTO.of(account))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(expenseFund, incomeFund))
        whenever(historicalPricingSdk.convert(userId, ConversionsRequest(emptyList())))
            .thenReturn(ConversionsResponse.empty())

        val fundTransactions = importFundConversionService.mapToFundRequest(userId, importParsedTransactions)

        assertThat(fundTransactions.transactions).hasSize(1)
        assertThat(fundTransactions.transactions[0].dateTime).isEqualTo(transactionDateTime)
        assertThat(fundTransactions.transactions[0].type).isEqualTo(FundTransactionType.TRANSFER)
        val records = fundTransactions.transactions[0].records
        assertThat(records).hasSize(2)

        val passThroughNegativeRecord =
            records.first { it.fundId == incomeFund.id && it.amount < BigDecimal.ZERO }
        assertThat(passThroughNegativeRecord.amount).isEqualByComparingTo(BigDecimal("-50.00"))
        assertThat(passThroughNegativeRecord.unit).isEqualTo(Currency.RON)
        assertThat(passThroughNegativeRecord.labels).isEmpty()

        val targetRecord = records
            .first { it.fundId == expenseFund.id && it.accountId == account.id }
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

        whenever(fundAccountSdk.listAccounts(userId)).thenReturn(ListTO.of(eurAccount, ronAccount))
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
        whenever(fundAccountSdk.listAccounts(userId)).thenReturn(ListTO.of(account("Cash RON")))
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
        whenever(fundAccountSdk.listAccounts(userId)).thenReturn(ListTO.of(account("Revolut")))
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

    @Test
    fun `should map investment transaction with OPEN_POSITION type`(): Unit = runBlocking {
        val transactionDateTime = LocalDateTime.parse("2024-07-22T09:17:00")
        val transactionExternalId = "investment-1"
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionExternalId = transactionExternalId,
                dateTime = transactionDateTime,
                records = listOf(
                    ImportParsedRecord(
                        AccountName("Broker USD"),
                        FundName("Investments"),
                        Currency.USD,
                        BigDecimal("-1000.00"),
                        listOf(Label("stock_purchase"))
                    ),
                    ImportParsedRecord(
                        AccountName("AAPL Stock"),
                        FundName("Investments"),
                        Symbol("AAPL"),
                        BigDecimal("5.0"),
                        listOf(Label("stock_purchase"))
                    )
                )
            )
        )
        val brokerAccount = account("Broker USD", Currency.USD)
        val stockAccount = account("AAPL Stock", Symbol("AAPL"))
        val investmentsFund = fund("Investments")
        whenever(fundAccountSdk.listAccounts(userId)).thenReturn(ListTO.of(brokerAccount, stockAccount))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(investmentsFund))
        whenever(historicalPricingSdk.convert(userId, ConversionsRequest(emptyList())))
            .thenReturn(ConversionsResponse.empty())

        val fundTransactions = importFundConversionService.mapToFundRequest(userId, importParsedTransactions)

        assertThat(fundTransactions.transactions).hasSize(1)
        val transaction = fundTransactions.transactions[0]
        assertThat(transaction.dateTime).isEqualTo(transactionDateTime)
        assertThat(transaction.externalId).isEqualTo(transactionExternalId)
        assertThat(transaction.type).isEqualTo(FundTransactionType.OPEN_POSITION)
        assertThat(transaction.records).hasSize(2)

        val currencyRecord = transaction.records.first { it.unit is Currency }
        val instrumentRecord = transaction.records.first { it.unit is Symbol }

        assertThat(currencyRecord.amount).isEqualByComparingTo(BigDecimal("-1000.00"))
        assertThat(currencyRecord.unit).isEqualTo(Currency.USD)
        assertThat(currencyRecord.accountId).isEqualTo(brokerAccount.id)
        assertThat(currencyRecord.fundId).isEqualTo(investmentsFund.id)
        assertThat(currencyRecord.labels).containsExactly(Label("stock_purchase"))

        assertThat(instrumentRecord.amount).isEqualByComparingTo(BigDecimal("5.0"))
        assertThat(instrumentRecord.unit).isEqualTo(Symbol("AAPL"))
        assertThat(instrumentRecord.accountId).isEqualTo(stockAccount.id)
        assertThat(instrumentRecord.fundId).isEqualTo(investmentsFund.id)
        assertThat(instrumentRecord.labels).containsExactly(Label("stock_purchase"))
    }

    @Test
    fun `should map investment transaction with CLOSE_POSITION type`(): Unit = runBlocking {
        val transactionDateTime = LocalDateTime.parse("2024-07-22T10:30:00")
        val transactionExternalId = "investment-2"
        val importParsedTransactions = listOf(
            ImportParsedTransaction(
                transactionExternalId = transactionExternalId,
                dateTime = transactionDateTime,
                records = listOf(
                    ImportParsedRecord(
                        AccountName("Broker USD"),
                        FundName("Investments"),
                        Currency.USD,
                        BigDecimal("1200.00"),
                        listOf(Label("stock_sale"))
                    ),
                    ImportParsedRecord(
                        AccountName("AAPL Stock"),
                        FundName("Investments"),
                        Symbol("AAPL"),
                        BigDecimal("-5.0"),
                        listOf(Label("stock_sale"))
                    )
                )
            )
        )
        val brokerAccount = account("Broker USD", Currency.USD)
        val stockAccount = account("AAPL Stock", Symbol("AAPL"))
        val investmentsFund = fund("Investments")
        whenever(fundAccountSdk.listAccounts(userId)).thenReturn(ListTO.of(brokerAccount, stockAccount))
        whenever(fundSdk.listFunds(userId)).thenReturn(ListTO.of(investmentsFund))
        whenever(historicalPricingSdk.convert(userId, ConversionsRequest(emptyList())))
            .thenReturn(ConversionsResponse.empty())

        val fundTransactions = importFundConversionService.mapToFundRequest(userId, importParsedTransactions)

        assertThat(fundTransactions.transactions).hasSize(1)
        val transaction = fundTransactions.transactions[0]
        assertThat(transaction.dateTime).isEqualTo(transactionDateTime)
        assertThat(transaction.externalId).isEqualTo(transactionExternalId)
        assertThat(transaction.type).isEqualTo(FundTransactionType.CLOSE_POSITION)
        assertThat(transaction.records).hasSize(2)

        val currencyRecord = transaction.records.first { it.unit is Currency }
        val instrumentRecord = transaction.records.first { it.unit is Symbol }

        assertThat(currencyRecord.amount).isEqualByComparingTo(BigDecimal("1200.00"))
        assertThat(currencyRecord.unit).isEqualTo(Currency.USD)
        assertThat(currencyRecord.accountId).isEqualTo(brokerAccount.id)
        assertThat(currencyRecord.fundId).isEqualTo(investmentsFund.id)
        assertThat(currencyRecord.labels).containsExactly(Label("stock_sale"))

        assertThat(instrumentRecord.amount).isEqualByComparingTo(BigDecimal("-5.0"))
        assertThat(instrumentRecord.unit).isEqualTo(Symbol("AAPL"))
        assertThat(instrumentRecord.accountId).isEqualTo(stockAccount.id)
        assertThat(instrumentRecord.fundId).isEqualTo(investmentsFund.id)
        assertThat(instrumentRecord.labels).containsExactly(Label("stock_sale"))
    }

    private fun account(name: String, unit: FinancialUnit = Currency.RON): AccountTO =
        AccountTO(
            id = randomUUID(),
            name = AccountName(name),
            unit = unit
        )

    private fun fund(name: String): FundTO =
        FundTO(
            id = randomUUID(),
            name = FundName(name),
        )
}
