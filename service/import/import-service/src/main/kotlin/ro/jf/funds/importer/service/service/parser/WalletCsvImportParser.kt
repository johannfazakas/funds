package ro.jf.funds.importer.service.service.parser

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toInstant
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.observability.tracing.withSpan
import ro.jf.funds.importer.api.model.ExchangeMatcherTO
import ro.jf.funds.importer.api.model.FundMatcherTO.*
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.service.domain.*
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import java.math.BigDecimal
import java.util.*


class WalletCsvImportParser(
    private val csvParser: CsvParser,
) : ImportParser {

    class WalletCsvRow(private val csvRow: CsvRow) {

        val accountName: String by lazy { csvRow.getString(ACCOUNT_NAME_COLUMN) }
        val amount: BigDecimal by lazy { csvRow.getBigDecimal(AMOUNT_COLUMN) }
        val currency: String by lazy { csvRow.getString(CURRENCY_COLUMN) }
        val labels: List<String> by lazy {
            csvRow.getString(LABEL_COLUMN)
                .split(LABEL_DELIMITER)
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
        val dateTime: LocalDateTime by lazy { csvRow.getDateTime(DATE_COLUMN, dateTimeFormat) }
        val note: String by lazy { csvRow.getString(NOTE_COLUMN) }

        companion object {
            private const val ACCOUNT_NAME_COLUMN = "account"
            private const val AMOUNT_COLUMN = "amount"
            private const val CURRENCY_COLUMN = "currency"
            private const val LABEL_COLUMN = "labels"
            private const val DATE_COLUMN = "date"
            private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
            private const val NOTE_COLUMN = "note"
            private const val LABEL_DELIMITER = "|"

            @OptIn(FormatStringsInDatetimeFormats::class)
            private val dateTimeFormat = LocalDateTime.Format { byUnicodePattern(DATE_FORMAT) }
        }
    }

    override fun parse(
        importConfiguration: ImportConfigurationTO, files: List<String>,
    ): List<ImportParsedTransaction> = withSpan("parse") {
        files
            .parse()
            .groupBy { it.transactionId(importConfiguration.exchangeMatchers) }
            .flatMap { (transactionId, rows) ->
                toTransactions(importConfiguration, transactionId, rows)
            }
    }

    private fun List<String>.parse(): List<WalletCsvRow> {
        val rawImportItems = this.map { csvParser.parse(it) }.flatten().map { WalletCsvRow(it) }
        if (rawImportItems.isEmpty())
            throw ImportDataException("No import reportdata")
        return rawImportItems
    }

    private fun WalletCsvRow.transactionId(exchangeMatchers: List<ExchangeMatcherTO>): String {
        return when (exchangeMatchers.getExchangeMatcher(labels)) {
            is ExchangeMatcherTO.ByLabel -> listOf(
                this.dateTime.inWholeMinutes(),
            ).joinToString()

            null -> listOf(
                this.note,
                this.amount.abs(),
                this.dateTime.toString()
            ).joinToString()
        }.let { UUID.nameUUIDFromBytes(it.toByteArray()).toString() }
    }

    private fun LocalDateTime.inWholeMinutes(): Long = this.toInstant(TimeZone.UTC).epochSeconds / 60

    private fun toTransactions(
        importConfiguration: ImportConfigurationTO,
        transactionId: String,
        walletCsvRows: List<WalletCsvRow>,
    ): List<ImportParsedTransaction> {
        return sequence {
            val mainRecords =
                extractRecords(walletCsvRows) { extractMainRecords(importConfiguration, it) }
                    ?: return@sequence
            val implicitTransferRecords =
                extractRecords(walletCsvRows) { extractImplicitTransferRecords(importConfiguration, it) }
            val dateTime = walletCsvRows.minOf { it.dateTime }
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
        walletCsvRows: List<WalletCsvRow>,
        rowExtractor: (WalletCsvRow) -> List<ImportParsedRecord>?,
    ): List<ImportParsedRecord>? {
        val parsedRecords: List<List<ImportParsedRecord>?> =
            walletCsvRows.map { rowExtractor(it) }
        if (parsedRecords.any { it == null }) return null
        return parsedRecords.filterNotNull().flatten().ifEmpty { null }
    }

    private fun extractMainRecords(
        importConfiguration: ImportConfigurationTO,
        row: WalletCsvRow,
    ): List<ImportParsedRecord>? {
        val importAccountName = row.accountName
        val accountName = importConfiguration.accountMatchers.getAccountMatcher(importAccountName)
            .accountName ?: return null // explicitly skipped account
        val fundMatcher = importConfiguration.fundMatchers.getFundMatcher(importAccountName, row.labels)
        val labels = importConfiguration.labelMatchers.getLabelMatchers(row.labels).map { it.label }
        val currency = Currency(row.currency)

        return when (fundMatcher) {
            is ByAccount, is ByLabel, is ByAccountLabel ->
                listOf(ImportParsedRecord(accountName, fundMatcher.fundName, currency, row.amount, labels))

            is ByAccountLabelWithPostTransfer ->
                listOf(ImportParsedRecord(accountName, fundMatcher.initialFundName, currency, row.amount, labels))

            is ByAccountLabelWithPreTransfer ->
                listOf(ImportParsedRecord(accountName, fundMatcher.fundName, currency, row.amount, labels))

            is ByLabelWithPostTransfer ->
                listOf(ImportParsedRecord(accountName, fundMatcher.initialFundName, currency, row.amount, labels))
        }
    }

    private fun extractImplicitTransferRecords(
        importConfiguration: ImportConfigurationTO,
        walletCsvRow: WalletCsvRow,
    ): List<ImportParsedRecord>? {
        val importAccountName = walletCsvRow.accountName
        val accountName = importConfiguration.accountMatchers.getAccountMatcher(importAccountName)
            .accountName ?: return null // explicitly skipped account
        val importLabels = walletCsvRow.labels
        val amount = walletCsvRow.amount
        val fundMatcher = importConfiguration.fundMatchers.getFundMatcher(importAccountName, importLabels)

        val currency = Currency(walletCsvRow.currency)
        return when (fundMatcher) {
            is ByAccount, is ByLabel, is ByAccountLabel ->
                emptyList() // No transfer needed

            is ByAccountLabelWithPostTransfer ->
                listOf(
                    ImportParsedRecord(accountName, fundMatcher.initialFundName, currency, amount.negate()),
                    ImportParsedRecord(accountName, fundMatcher.fundName, currency, amount)
                )

            is ByAccountLabelWithPreTransfer ->
                listOf(
                    ImportParsedRecord(accountName, fundMatcher.initialFundName, currency, amount),
                    ImportParsedRecord(accountName, fundMatcher.fundName, currency, amount.negate())
                )

            is ByLabelWithPostTransfer ->
                listOf(
                    ImportParsedRecord(accountName, fundMatcher.initialFundName, currency, amount.negate()),
                    ImportParsedRecord(accountName, fundMatcher.fundName, currency, amount)
                )
        }
    }
}
