package ro.jf.funds.importer.service.service.conversion

import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.fund.api.model.CreateFundRecordTO
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import java.math.BigDecimal
import java.util.*

private val log = logger { }

class ImportFundConversionService(
    private val accountSdk: AccountSdk,
    private val fundSdk: FundSdk,
    private val historicalPricingAdapter: HistoricalPricingAdapter,
    private val converterRegistry: ImportFundConverterRegistry,
) {
    suspend fun mapToFundRequest(
        userId: UUID,
        parsedTransactions: List<ImportParsedTransaction>,
    ): CreateFundTransactionsTO {
        log.info { "Handling import >> user = $userId items size = ${parsedTransactions.size}." }
        val importResourceContext = createImportResourceContext(userId)
        return parsedTransactions.toFundTransactions(importResourceContext).toRequest()
    }

    private suspend fun List<ImportParsedTransaction>.toFundTransactions(
        importResourceContext: ImportResourceContext,
    ): List<ImportFundTransaction> {
        // TODO(Johann) this surely can be written more nicely
        val parsedTransactionsToStrategy =
            this.map { it to it.getConverterStrategy(importResourceContext) }
        val requiredConversions = parsedTransactionsToStrategy
            .flatMap { (transaction, strategy) ->
                strategy.getRequiredConversions(transaction) {
                    importResourceContext.getAccount(
                        accountName
                    )
                }
            }
            .toSet()
        val conversionContext = createConversionContext(requiredConversions)

        return parsedTransactionsToStrategy.map { (transaction, strategy) ->
            strategy.mapToFundTransaction(
                transaction,
                { importResourceContext.getFundId(fundName) },
                { importResourceContext.getAccount(accountName) },
                { conversionContext.getConversionRate(this) }
            )
        }
    }

    private fun ImportParsedTransaction.getConverterStrategy(
        importResourceContext: ImportResourceContext,
    ): ImportFundConverter {
        return converterRegistry.all()
            .firstOrNull { it.matches(this, { importResourceContext.getAccount(accountName) }) }
            ?: throw ImportDataException("Unrecognized transaction type: $this")
    }

    private suspend fun createImportResourceContext(userId: UUID) = ImportResourceContext(
        accountSdk.listAccounts(userId).items,
        fundSdk.listFunds(userId).items
    )

    // TODO(Johann) could be extracted
    private class ImportResourceContext(
        accounts: List<AccountTO>,
        funds: List<FundTO>,
    ) {
        private val accountByName: Map<AccountName, AccountTO> = accounts.associateBy { it.name }
        private val fundIdByName: Map<FundName, UUID> = funds.associate { it.name to it.id }

        fun getAccount(accountName: AccountName) = accountByName[accountName]
            ?: throw ImportDataException("Record account not found: $accountName")

        fun getFundId(fundName: FundName) = fundIdByName[fundName]
            ?: throw ImportDataException("Record fund not found: $fundName")
    }

    private suspend fun createConversionContext(
        requests: Set<ConversionRequest>,
    ): ConversionContext {
        // TODO(Johann) could be simplified, or written more nicely
        val conversions = requests
            .groupBy(ConversionRequest::currencyPair) { it }
            .mapValues { (currencyPair, requests) ->
                val dates = requests.map { it.date }.toList()
                val historicalPrices = historicalPricingAdapter.convertCurrencies(
                    sourceCurrency = currencyPair.sourceCurrency,
                    targetCurrency = currencyPair.targetCurrency,
                    dates = dates
                )
                val missingDates = dates - (historicalPrices.map { it.date }.toSet())
                if (missingDates.isNotEmpty()) {
                    throw ImportDataException("Missing historical prices for conversion: $currencyPair on dates: $missingDates")
                }
                historicalPrices
            }
            .flatMap { (currencyPair, historicalPrices) ->
                historicalPrices.map {
                    ConversionRequest(
                        date = it.date,
                        currencyPair = currencyPair
                    ) to it.price
                }
            }
            .toMap()
        return ConversionContext(conversions)
    }

    private class ConversionContext(
        private val conversions: Map<ConversionRequest, BigDecimal>,
    ) {
        fun getConversionRate(
            request: ConversionRequest,
        ): BigDecimal {
            return conversions[request]
                ?: throw ImportDataException("Missing historical price for conversion: $request")
        }

        fun getConversionRate(
            sourceCurrency: Currency,
            targetCurrency: Currency,
            date: LocalDate,
        ): BigDecimal {
            return getConversionRate(ConversionRequest(date, CurrencyPair(sourceCurrency, targetCurrency)))
        }
    }

    // TODO(Johann) should extract? together with conversion context?
    data class ConversionRequest(
        val date: LocalDate,
        val currencyPair: CurrencyPair,
    )

    data class CurrencyPair(
        val sourceCurrency: Currency,
        val targetCurrency: Currency,
    )

    private fun List<ImportFundTransaction>.toRequest(): CreateFundTransactionsTO =
        CreateFundTransactionsTO(map { it.toRequest() })


    data class ImportFundRecord(
        val fundId: UUID,
        val accountId: UUID,
        val amount: BigDecimal,
        val unit: FinancialUnit,
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