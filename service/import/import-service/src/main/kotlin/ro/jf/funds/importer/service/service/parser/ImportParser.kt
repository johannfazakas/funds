package ro.jf.funds.importer.service.service.parser

import ro.jf.funds.importer.service.domain.*
import ro.jf.funds.importer.service.domain.exception.ImportDataException

abstract class ImportParser {
    fun parse(
        matchers: ImportMatchers,
        content: String,
    ): List<Result<ImportParsedTransaction>> {
        return parseItems(content)
            .groupBy { it.transactionId { item -> isExchange(matchers, item) } }
            .flatMap { (transactionId, items) -> toTransactions(matchers, transactionId, items) }
    }

    protected abstract fun parseItems(content: String): List<ImportItem>

    private fun toTransactions(
        matchers: ImportMatchers,
        transactionId: String,
        items: List<ImportItem>,
    ): List<Result<ImportParsedTransaction>> {
        val dateTime = items.minOf { it.dateTime }

        val main = extractTransaction(transactionId, dateTime, items) { extractMainRecord(matchers, it)?.let(::listOf) }
        val implicit = if (main.getOrNull() != null)
            extractTransaction("$transactionId-fund-transfer", dateTime, items) { extractImplicitTransferRecords(matchers, it) }
        else Result.success(null)
        return listOf(main, implicit).mapNotNull { result ->
            result.getOrNull()?.let { Result.success(it) } ?: result.exceptionOrNull()?.let { Result.failure(it) }
        }
    }

    private fun extractTransaction(
        transactionId: String,
        dateTime: kotlinx.datetime.LocalDateTime,
        items: List<ImportItem>,
        rowExtractor: (ImportItem) -> List<ImportParsedRecord>?,
    ): Result<ImportParsedTransaction?> {
        val results = items.map { item -> runCatching { rowExtractor(item) } }
        results.mapNotNull { it.exceptionOrNull() as? ImportDataException }
            .reduceOrNull { acc, e -> acc + e }
            ?.let { return Result.failure(it) }
        val extractedLists = results.map { it.getOrThrow() }
        if (extractedLists.any { it == null }) return Result.success(null)
        val records = extractedLists.filterNotNull().flatten()
        if (records.isEmpty()) return Result.success(null)
        return Result.success(ImportParsedTransaction(transactionId, dateTime, records))
    }

    private fun extractMainRecord(
        matchers: ImportMatchers,
        item: ImportItem,
    ): ImportParsedRecord? {
        val importAccountName = item.accountName
        val accountMatcher = matchers.getAccountMatcher(importAccountName)
        val accountId = accountMatcher.accountId ?: return null
        val categoryMatcher = matchers.findCategoryMatcher(item.labels)
        val categoryId = categoryMatcher?.categoryId
        val fundMatcher = matchers.getFundMatcher(accountId)
        val resolved = fundMatcher.resolve(categoryId)
        val recordFundId = resolved.intermediaryFundId ?: resolved.fundId
        val note = item.note.takeIf { it.isNotBlank() }
        return ImportParsedRecord(accountId, recordFundId, item.unit, item.amount, categoryId, note)
    }

    private fun extractImplicitTransferRecords(
        matchers: ImportMatchers,
        item: ImportItem,
    ): List<ImportParsedRecord> {
        val importAccountName = item.accountName
        val accountMatcher = matchers.getAccountMatcher(importAccountName)
        if (accountMatcher.skipped) throw ImportDataException("Account skipped on implicit transfer: $item")
        val accountId = accountMatcher.accountId ?: return emptyList()
        val categoryMatcher = matchers.findCategoryMatcher(item.labels)
        val categoryId = categoryMatcher?.categoryId
        val fundMatcher = matchers.getFundMatcher(accountId)
        val resolved = fundMatcher.resolve(categoryId)
        val intermediaryFundId = resolved.intermediaryFundId ?: return emptyList()
        val amount = item.amount
        return listOf(
            ImportParsedRecord(accountId, intermediaryFundId, item.unit, amount.negate()),
            ImportParsedRecord(accountId, resolved.fundId, item.unit, amount)
        )
    }

    private fun isExchange(matchers: ImportMatchers, item: ImportItem): Boolean =
        matchers.getExchangeMatcher(item.labels) != null
}
