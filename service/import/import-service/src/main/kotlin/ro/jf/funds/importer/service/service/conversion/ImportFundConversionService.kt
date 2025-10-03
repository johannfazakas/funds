package ro.jf.funds.importer.service.service.conversion

import mu.KotlinLogging.logger
import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.service.conversion.strategy.ImportTransactionConverterRegistry
import java.util.*

private val log = logger { }

class ImportFundConversionService(
    private val accountService: AccountService,
    private val fundService: FundService,
    private val converterRegistry: ImportTransactionConverterRegistry,
    private val historicalPricingSdk: HistoricalPricingSdk,
) {
    suspend fun mapToFundRequest(
        userId: UUID,
        parsedTransactions: List<ImportParsedTransaction>,
    ): CreateFundTransactionsTO = withSuspendingSpan {
        log.info { "Handling import >> user = $userId items size = ${parsedTransactions.size}." }
        val accountStore = accountService.getAccountStore(userId)
        val fundStore = fundService.getFundStore(userId)
        parsedTransactions.toFundTransactions(userId, accountStore, fundStore).let(::CreateFundTransactionsTO)
    }

    private suspend fun List<ImportParsedTransaction>.toFundTransactions(
        userId: UUID,
        accountStore: Store<AccountName, AccountTO>,
        fundStore: Store<FundName, FundTO>,
    ): List<CreateFundTransactionTO> {
        val transactionsToStrategy = map { it to it.getConverterStrategy(accountStore) }

        val conversions = transactionsToStrategy
            .flatMap { (transaction, strategy) -> strategy.getRequiredConversions(transaction, accountStore) }
            .map { ConversionRequest(it.sourceCurrency, it.targetCurrency, it.date) }
            .distinct()
            .let { historicalPricingSdk.convert(userId, ConversionsRequest(it)) }

        return transactionsToStrategy
            .flatMap { (transaction, strategy) ->
                strategy.mapToFundTransactions(transaction, conversions, fundStore, accountStore)
            }
    }

    private fun ImportParsedTransaction.getConverterStrategy(
        accountStore: Store<AccountName, AccountTO>,
    ): ImportTransactionConverter {
        return converterRegistry.converters
            .firstOrNull { it.matches(this, accountStore) }
            ?: throw ImportDataException("Unrecognized transaction type: $this")
    }
}
