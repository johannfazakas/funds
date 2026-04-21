package ro.jf.funds.importer.service.service.conversion

import mu.KotlinLogging.logger
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.*
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
    private val accountService: AccountService,
    private val fundService: FundService,
    private val categoryService: CategoryService,
    private val converterRegistry: ImportTransactionConverterRegistry,
    private val conversionSdk: ConversionSdk,
) {
    suspend fun mapToFundRequest(
        userId: Uuid,
        parsedTransactions: List<ImportParsedTransaction>,
    ): List<Result<CreateTransactionTO>> = withSuspendingSpan {
        log.info { "Handling import >> user = $userId items size = ${parsedTransactions.size}." }
        val accountStore = accountService.getAccountStore(userId)
        val fundStore = fundService.getFundStore(userId)
        val categoryStore = categoryService.getCategoryStore(userId)

        val importTransactionsToConverter = parsedTransactions
            .map { transaction -> runCatching { transaction to transaction.getConverterStrategy(accountStore) } }
        val conversions = fetchConversions(importTransactionsToConverter.mapNotNull { it.getOrNull() }, accountStore)

        importTransactionsToConverter.map { result ->
            result.fold(
                onSuccess = { (transaction, strategy) ->
                    convertTransaction(transaction, strategy, conversions, fundStore, accountStore, categoryStore)
                },
                onFailure = { Result.failure(ImportDataException(it)) }
            )
        }
    }

    private suspend fun fetchConversions(
        matched: List<Pair<ImportParsedTransaction, ImportTransactionConverter>>,
        accountStore: Store<AccountName, AccountTO>,
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
        fundStore: Store<FundName, FundTO>,
        accountStore: Store<AccountName, AccountTO>,
        categoryStore: Store<String, CategoryTO>,
    ): Result<CreateTransactionTO> {
        val mappingResult = runCatching {
            strategy.mapToTransaction(transaction, conversions, fundStore, accountStore)
        }
        val mappingErrors = mappingResult.exceptionOrNull()
            ?.let { listOf(ImportDataException(it)) }
            ?: emptyList()
        val categoryErrors = validateCategories(transaction, categoryStore)

        val allErrors = categoryErrors + mappingErrors
        if (allErrors.isNotEmpty()) return Result.failure(allErrors.reduce { acc, e -> acc + e })

        return mappingResult
    }

    private fun validateCategories(
        transaction: ImportParsedTransaction,
        categoryStore: Store<String, *>,
    ): List<ImportDataException> {
        return transaction.records
            .mapNotNull { it.category }
            .map { it.value }
            .distinct()
            .mapNotNull { category ->
                runCatching { categoryStore[category] }.exceptionOrNull()?.let { ImportDataException(it) }
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
