package ro.jf.funds.importer.service.service.conversion

import mu.KotlinLogging.logger
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CategoryTO
import ro.jf.funds.fund.api.model.CreateTransactionTO
import ro.jf.funds.fund.sdk.AccountSdk
import ro.jf.funds.fund.sdk.CategorySdk
import ro.jf.funds.conversion.api.model.ConversionRequest
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import com.benasher44.uuid.Uuid
import ro.jf.funds.importer.service.service.conversion.strategy.ImportTransactionConverterRegistry

private val log = logger { }

class ImportFundConversionService(
    private val accountSdk: AccountSdk,
    private val categorySdk: CategorySdk,
    private val converterRegistry: ImportTransactionConverterRegistry,
    private val conversionSdk: ConversionSdk,
) {
    suspend fun mapToFundRequest(
        userId: Uuid,
        parsedTransactions: List<ImportParsedTransaction>,
    ): List<Result<CreateTransactionTO>> = withSuspendingSpan {
        log.info { "Handling import >> user = $userId items size = ${parsedTransactions.size}." }
        val accountStore = Store(accountSdk.listAccounts(userId).items) { it.id }
        val categoryStore = Store(categorySdk.listCategories(userId)) { it.id }

        val importTransactionsToConverter = parsedTransactions
            .map { transaction -> runCatching { transaction to transaction.getConverterStrategy(accountStore) } }
        val conversions = fetchConversions(importTransactionsToConverter.mapNotNull { it.getOrNull() }, accountStore)

        importTransactionsToConverter.map { result ->
            result.fold(
                onSuccess = { (transaction, strategy) ->
                    convertTransaction(transaction, strategy, conversions, accountStore, categoryStore)
                },
                onFailure = { Result.failure(ImportDataException(it)) }
            )
        }
    }

    private suspend fun fetchConversions(
        matched: List<Pair<ImportParsedTransaction, ImportTransactionConverter>>,
        accountStore: Store<AccountTO>,
    ): ConversionsResponse {
        val requests = matched
            .flatMap { (transaction, strategy) -> strategy.getRequiredConversions(transaction, accountStore) }
            .map { ConversionRequest(it.sourceCurrency, it.targetCurrency, it.date) }
            .distinct()
        return conversionSdk.convert(ConversionsRequest(requests))
    }

    private fun convertTransaction(
        transaction: ImportParsedTransaction,
        strategy: ImportTransactionConverter,
        conversions: ConversionsResponse,
        accountStore: Store<AccountTO>,
        categoryStore: Store<CategoryTO>,
    ): Result<CreateTransactionTO> {
        return runCatching {
            strategy.mapToTransaction(transaction, conversions, accountStore, categoryStore)
        }.recoverCatching { Result.failure<CreateTransactionTO>(ImportDataException(it)); throw it }
    }

    private fun ImportParsedTransaction.getConverterStrategy(
        accountStore: Store<AccountTO>,
    ): ImportTransactionConverter {
        return converterRegistry.converters
            .firstOrNull { it.matches(this, accountStore) }
            ?: throw ImportDataException("Unrecognized transaction type: $this")
    }
}
