package ro.jf.funds.importer.service.service.parser

import ro.jf.funds.importer.service.domain.*

abstract class ImportParser {
    fun parse(matchers: ImportMatchers, files: List<String>): List<ImportParsedTransaction> {
        return parseItems(files)
            .groupBy { it.transactionId { item -> isExchange(matchers, item) } }
            .flatMap { (transactionId, items) -> toTransactions(matchers, transactionId, items) }
    }

    protected abstract fun parseItems(files: List<String>): List<ImportItem>

    private fun toTransactions(
        matchers: ImportMatchers,
        transactionId: String,
        items: List<ImportItem>,
    ): List<ImportParsedTransaction> {
        return sequence {
            val mainRecords =
                extractRecords(items) { extractMainRecords(matchers, it) }
                    ?: return@sequence
            val implicitTransferRecords =
                extractRecords(items) { extractImplicitTransferRecords(matchers, it) }
            val dateTime = items.minOf { it.dateTime }
            yield(
                ImportParsedTransaction(
                    transactionExternalId = transactionId,
                    dateTime = dateTime,
                    records = mainRecords
                )
            )
            if (implicitTransferRecords != null) {
                yield(
                    ImportParsedTransaction(
                        transactionExternalId = "$transactionId-fund-transfer",
                        dateTime = dateTime,
                        records = implicitTransferRecords
                    )
                )
            }
        }.toList()
    }

    private fun extractRecords(
        items: List<ImportItem>,
        rowExtractor: (ImportItem) -> List<ImportParsedRecord>?,
    ): List<ImportParsedRecord>? {
        val parsedRecords: List<List<ImportParsedRecord>?> = items.map { rowExtractor(it) }
        if (parsedRecords.any { it == null }) return null
        return parsedRecords.filterNotNull().flatten().ifEmpty { null }
    }

    private fun extractMainRecords(
        matchers: ImportMatchers,
        item: ImportItem,
    ): List<ImportParsedRecord>? {
        val importAccountName = item.accountName
        val accountName = matchers.getAccountMatcher(importAccountName)
            .accountName ?: return null
        val fundMatcher = matchers.getFundMatcher(importAccountName, item.labels)
        val labels = matchers.getLabelMatchers(item.labels).map { it.label }
        val note = item.note.takeIf { it.isNotBlank() }
        val recordFund = fundMatcher.intermediaryFundName ?: fundMatcher.fundName
        return listOf(ImportParsedRecord(accountName, recordFund, item.unit, item.amount, labels, note))
    }

    private fun extractImplicitTransferRecords(
        matchers: ImportMatchers,
        item: ImportItem,
    ): List<ImportParsedRecord>? {
        val importAccountName = item.accountName
        val accountName = matchers.getAccountMatcher(importAccountName)
            .accountName ?: return null
        val fundMatcher = matchers.getFundMatcher(importAccountName, item.labels)
        val intermediary = fundMatcher.intermediaryFundName ?: return emptyList()
        val amount = item.amount
        return listOf(
            ImportParsedRecord(accountName, intermediary, item.unit, amount.negate()),
            ImportParsedRecord(accountName, fundMatcher.fundName, item.unit, amount)
        )
    }

    private fun isExchange(matchers: ImportMatchers, item: ImportItem): Boolean =
        matchers.getExchangeMatcher(item.labels) != null
}
