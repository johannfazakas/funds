package ro.jf.funds.importer.service.service.parser

import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.service.domain.*

abstract class ImportParser {
    fun parse(importConfiguration: ImportConfigurationTO, files: List<String>): List<ImportParsedTransaction> {
        return parseItems(files)
            .groupBy { it.transactionId { item -> isExchange(importConfiguration, item) } }
            .flatMap { (transactionId, items) -> toTransactions(importConfiguration, transactionId, items) }
    }

    protected abstract fun parseItems(files: List<String>): List<ImportItem>

    private fun toTransactions(
        importConfiguration: ImportConfigurationTO,
        transactionId: String,
        items: List<ImportItem>,
    ): List<ImportParsedTransaction> {
        return sequence {
            val mainRecords =
                extractRecords(items) { extractMainRecords(importConfiguration, it) }
                    ?: return@sequence
            val implicitTransferRecords =
                extractRecords(items) { extractImplicitTransferRecords(importConfiguration, it) }
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
        importConfiguration: ImportConfigurationTO,
        item: ImportItem,
    ): List<ImportParsedRecord>? {
        val importAccountName = item.accountName
        val accountName = importConfiguration.accountMatchers.getAccountMatcher(importAccountName)
            .accountName ?: return null
        val fundMatcher = importConfiguration.fundMatchers.getFundMatcher(importAccountName, item.labels)
        val labels = importConfiguration.labelMatchers.getLabelMatchers(item.labels).map { it.label }
        val note = item.note.takeIf { it.isNotBlank() }
        val recordFund = fundMatcher.intermediaryFundName ?: fundMatcher.fundName
        return listOf(ImportParsedRecord(accountName, recordFund, item.unit, item.amount, labels, note))
    }

    private fun extractImplicitTransferRecords(
        importConfiguration: ImportConfigurationTO,
        item: ImportItem,
    ): List<ImportParsedRecord>? {
        val importAccountName = item.accountName
        val accountName = importConfiguration.accountMatchers.getAccountMatcher(importAccountName)
            .accountName ?: return null
        val fundMatcher = importConfiguration.fundMatchers.getFundMatcher(importAccountName, item.labels)
        val intermediary = fundMatcher.intermediaryFundName ?: return emptyList()
        val amount = item.amount
        return listOf(
            ImportParsedRecord(accountName, intermediary, item.unit, amount.negate()),
            ImportParsedRecord(accountName, fundMatcher.fundName, item.unit, amount)
        )
    }

    private fun isExchange(importConfiguration: ImportConfigurationTO, item: ImportItem): Boolean =
        importConfiguration.exchangeMatchers.getExchangeMatcher(item.labels) != null
}
