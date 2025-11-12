package ro.jf.funds.importer.service.service.conversion

import mu.KotlinLogging.logger
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.*
import ro.jf.funds.conversion.api.model.ConversionRequest
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.sdk.ConversionSdk
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
    private val conversionSdk: ConversionSdk,
) {
    suspend fun mapToFundRequest(
        userId: UUID,
        parsedTransactions: List<ImportParsedTransaction>,
    ): CreateTransactionsTO = withSuspendingSpan {
        log.info { "Handling import >> user = $userId items size = ${parsedTransactions.size}." }
        val accountStore = accountService.getAccountStore(userId)
        val fundStore = fundService.getFundStore(userId)
        parsedTransactions.toFundTransactions(accountStore, fundStore).let(::CreateTransactionsTO)
    }

    private suspend fun List<ImportParsedTransaction>.toFundTransactions(
        accountStore: Store<AccountName, AccountTO>,
        fundStore: Store<FundName, FundTO>,
    ): List<CreateTransactionTO> {
        val transactionsToStrategy = map { it to it.getConverterStrategy(accountStore) }

        val conversions = transactionsToStrategy
            .flatMap { (transaction, strategy) -> strategy.getRequiredConversions(transaction, accountStore) }
            .map { ConversionRequest(it.sourceCurrency, it.targetCurrency, it.date) }
            .distinct()
            .let { conversionSdk.convert(ConversionsRequest(it)) }

        return transactionsToStrategy
            .flatMap { (transaction, strategy) ->
                strategy.mapToTransactions(transaction, conversions, fundStore, accountStore)
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
