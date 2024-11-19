package ro.jf.funds.importer.service.service

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import mu.KotlinLogging.logger
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.service.ImportFundMapper.ImportFundTransaction.Type.SINGLE_RECORD
import ro.jf.funds.importer.service.service.ImportFundMapper.ImportFundTransaction.Type.TRANSFER
import java.math.BigDecimal
import java.util.*

private val log = logger { }

class ImportFundMapper(
    private val accountSdk: AccountSdk,
    private val fundSdk: FundSdk,
    private val historicalPricingAdapter: HistoricalPricingAdapter,
) {
    suspend fun mapToFundRequest(
        userId: UUID,
        parsedTransactions: List<ImportParsedTransaction>
    ): CreateFundTransactionsTO {
        log.info { "Handling import >> user = $userId items size = ${parsedTransactions.size}." }
        val importResourceContext = createImportResourceContext(userId)
        val conversionContext = createConversionContext(importResourceContext, parsedTransactions)
        return parsedTransactions
            .map { it.toTransactionRequest(importResourceContext, conversionContext) }
            .toRequest()
    }

    private fun ImportParsedTransaction.toTransactionRequest(
        importResourceContext: ImportResourceContext,
        conversionContext: ConversionContext
    ): ImportFundTransaction {
        val type: ImportFundTransaction.Type = resolveTransactionType(importResourceContext)
        return when (type) {
            SINGLE_RECORD -> toSingleRecordFundTransaction(importResourceContext, conversionContext)
            TRANSFER -> toTransferFundTransaction(importResourceContext, conversionContext)
        }
    }

    private fun ImportParsedTransaction.resolveTransactionType(importResourceContext: ImportResourceContext): ImportFundTransaction.Type {
        return ImportFundTransaction.Type.entries
            .map { type -> type to type.matcher(importResourceContext) }
            .filter { (_, matcher) -> matcher.invoke(this) }
            .map { (type, _) -> type }
            .firstOrNull()
            ?: throw ImportDataException("Unrecognized transaction type: $this")
    }

    private fun ImportFundTransaction.Type.matcher(importResourceContext: ImportResourceContext): (ImportParsedTransaction) -> Boolean {
        return when (this) {
            SINGLE_RECORD -> { transaction ->
                transaction.records.size == 1 && transaction.records.first()
                    .let { importResourceContext.getAccount(it.accountName).unit is Currency }
            }

            TRANSFER -> { transaction ->
                val accounts = transaction.records.map { importResourceContext.getAccount(it.accountName) }
                val targetCurrencies = accounts.map { it.unit }.toSet()
                val sourceCurrencies = transaction.records.map { it.unit }.toSet()
                transaction.records.size == 2 &&
                        targetCurrencies.size == 1 && sourceCurrencies.size == 1 &&
                        targetCurrencies.all { it is Currency } && sourceCurrencies.all { it is Currency } &&
                        transaction.records.sumOf { it.amount }.compareTo(BigDecimal.ZERO) == 0
            }
        }
    }

    private fun ImportParsedTransaction.toSingleRecordFundTransaction(
        importResourceContext: ImportResourceContext,
        conversionContext: ConversionContext
    ): ImportFundTransaction {
        return ImportFundTransaction(
            dateTime = dateTime,
            type = SINGLE_RECORD,
            records = records.map { record ->
                record.toImportCurrencyFundRecord(dateTime.date, importResourceContext, conversionContext)
            }
        )
    }

    private fun ImportParsedTransaction.toTransferFundTransaction(
        importResourceContext: ImportResourceContext,
        conversionContext: ConversionContext
    ): ImportFundTransaction {
        return ImportFundTransaction(
            dateTime = dateTime,
            type = TRANSFER,
            records = records.map { record ->
                record.toImportCurrencyFundRecord(dateTime.date, importResourceContext, conversionContext)
            }
        )
    }

    private fun ImportParsedRecord.toImportCurrencyFundRecord(
        date: LocalDate,
        importResourceContext: ImportResourceContext,
        conversionContext: ConversionContext
    ): ImportFundRecord {
        val account = importResourceContext.getAccount(accountName)
        return ImportFundRecord(
            fundId = importResourceContext.getFundId(fundName),
            accountId = account.id,
            amount = if (unit == account.unit) {
                amount
            } else {
                conversionContext.getConversionRate(
                    sourceCurrency = unit as Currency,
                    targetCurrency = account.unit as Currency,
                    date = date
                ) * amount
            },
            unit = importResourceContext.getAccount(accountName).unit as Currency,
        )
    }

    private suspend fun createImportResourceContext(userId: UUID) = ImportResourceContext(
        accountSdk.listAccounts(userId).items,
        fundSdk.listFunds(userId).items
    )

    private class ImportResourceContext(
        accounts: List<AccountTO>,
        funds: List<FundTO>
    ) {
        private val accountByName: Map<AccountName, AccountTO> = accounts.associateBy { it.name }
        private val fundIdByName: Map<FundName, UUID> = funds.associate { it.name to it.id }

        fun getAccount(accountName: AccountName) = accountByName[accountName]
            ?: throw ImportDataException("Record account not found: $accountName")

        fun getFundId(fundName: FundName) = fundIdByName[fundName]
            ?: throw ImportDataException("Record fund not found: $fundName")
    }

    private suspend fun createConversionContext(
        importResourceContext: ImportResourceContext,
        parsedTransactions: List<ImportParsedTransaction>,
    ): ConversionContext {
        return parsedTransactions
            .asSequence()
            .flatMap { transaction ->
                transaction.records.map {
                    Conversion(
                        it.unit as Currency,
                        importResourceContext.getAccount(it.accountName).unit as Currency
                    ) to transaction.dateTime.date
                }
            }
            .filter { (conversion, date) -> conversion.sourceCurrency != conversion.targetCurrency }
            .groupBy({ (conversion, _) -> conversion }) { (_, date) -> date }
            .map { (conversion, dates) -> conversion to dates.toSet() }
            .associate { (conversion, dates) ->
                val historicalPrices = historicalPricingAdapter.convertCurrencies(
                    sourceCurrency = conversion.sourceCurrency,
                    targetCurrency = conversion.targetCurrency,
                    dates = dates.toList()
                )
                val missingDates = dates - (historicalPrices.map { it.date }.toSet())
                if (missingDates.isNotEmpty()) {
                    throw ImportDataException("Missing historical prices for conversion: $conversion on dates: $missingDates")
                }
                conversion to historicalPrices.associate { it.date to it.price }
            }
            .let { ConversionContext(it) }
    }

    private class ConversionContext(
        private val conversions: Map<Conversion, Map<LocalDate, BigDecimal>>
    ) {
        fun getConversionRate(
            sourceCurrency: Currency,
            targetCurrency: Currency,
            date: LocalDate
        ): BigDecimal {
            return conversions[Conversion(sourceCurrency, targetCurrency)]?.get(date)
                ?: throw ImportDataException("Missing historical price for conversion: $sourceCurrency -> $targetCurrency on date: $date")
        }
    }

    private data class Conversion(
        val sourceCurrency: Currency,
        val targetCurrency: Currency
    )

    private fun List<ImportFundTransaction>.toRequest(): CreateFundTransactionsTO =
        CreateFundTransactionsTO(map { it.toRequest() })

    data class ImportFundTransaction(
        val dateTime: LocalDateTime,
        val type: Type,
        val records: List<ImportFundRecord>
    ) {
        enum class Type {
            SINGLE_RECORD,
            TRANSFER,
            // TODO(Johann) add EXCHANGE type
        }

        fun toRequest(): CreateFundTransactionTO {
            return CreateFundTransactionTO(
                dateTime = dateTime,
                records = records.map { it.toRequest() }
            )
        }
    }

    data class ImportFundRecord(
        val fundId: UUID,
        val accountId: UUID,
        val amount: BigDecimal,
        val unit: FinancialUnit
    ) {
        fun toRequest(): CreateFundRecordTO {
            return CreateFundRecordTO(
                fundId = fundId,
                accountId = accountId,
                amount = amount,
                unit = unit
            )
        }
    }
}
