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
import java.util.*

private const val ACCOUNT_NAME_COLUMN = "account"
private const val AMOUNT_COLUMN = "amount"
private const val CURRENCY_COLUMN = "currency"
private const val LABEL_COLUMN = "labels"
private const val DATE_COLUMN = "date"
private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
private const val NOTE_COLUMN = "note"
private const val LABEL_DELIMITER = "|"

class WalletCsvImportParser(
    private val csvParser: CsvParser,
) : ImportParser {
    @OptIn(FormatStringsInDatetimeFormats::class)
    private val dateTimeFormat = LocalDateTime.Format { byUnicodePattern(DATE_FORMAT) }

    override fun parse(
        importConfiguration: ImportConfigurationTO, files: List<String>,
    ): List<ImportParsedTransaction> = withSpan("parse") {
        files
            .parse()
            .groupBy { it.transactionId(importConfiguration.exchangeMatchers) }
            .map { (transactionId, csvRows) -> toTransaction(importConfiguration, transactionId, csvRows) }
    }

    private fun List<String>.parse(): List<CsvRow> {
        val rawImportItems = this.map { csvParser.parse(it) }.flatten()
        if (rawImportItems.isEmpty())
            throw ImportDataException("No import data")
        return rawImportItems
    }

    private fun CsvRow.transactionId(exchangeMatchers: List<ExchangeMatcherTO>): String {
        return when (exchangeMatchers.getExchangeMatcher(labels())) {
            is ExchangeMatcherTO.ByLabel -> listOf(
                this.getDateTime(DATE_COLUMN, dateTimeFormat).inWholeMinutes(),
            ).joinToString()

            // TODO(Johann) could have the same rule for all. hash the transaction id based on note and date. that's all. get rid of exchange labels.
            null -> listOf(
                this.getString(NOTE_COLUMN),
                this.getBigDecimal(AMOUNT_COLUMN).abs(),
                this.getString(DATE_COLUMN)
            ).joinToString()
        }.let { UUID.nameUUIDFromBytes(it.toByteArray()).toString() }
    }

    private fun LocalDateTime.inWholeMinutes(): Long = this.toInstant(TimeZone.UTC).epochSeconds / 60

    private fun toTransaction(
        importConfiguration: ImportConfigurationTO,
        transactionId: String,
        csvRows: List<CsvRow>,
    ): ImportParsedTransaction {
        return ImportParsedTransaction(
            transactionExternalId = transactionId,
            dateTime = csvRows.minOf { it.getDateTime(DATE_COLUMN, dateTimeFormat) },
            records = csvRows.flatMap { csvRow -> toImportRecords(importConfiguration, csvRow) }
        )
    }

    private fun toImportRecords(importConfiguration: ImportConfigurationTO, csvRow: CsvRow): List<ImportParsedRecord> {
        val importAccountName = csvRow.getString(ACCOUNT_NAME_COLUMN)
        val accountName = importConfiguration.accountMatchers.getAccountName(importAccountName)
        val importLabels = csvRow.getString(LABEL_COLUMN).labels()
        val currency = csvRow.getString(CURRENCY_COLUMN)
        val amount = csvRow.getBigDecimal(AMOUNT_COLUMN)
        val fundMatcher = importConfiguration.fundMatchers.getFundMatcher(importAccountName, importLabels)
        val labelMatchers = importConfiguration.labelMatchers.getLabelMatchers(importLabels)
        val labels = labelMatchers.map { it.label }

        return when (fundMatcher) {
            is ByAccount, is ByLabel, is ByAccountLabel ->
                listOf(ImportParsedRecord(accountName, fundMatcher.fundName, Currency(currency), amount, labels))

            is ByAccountLabelWithTransfer -> {
                listOf(
                    ImportParsedRecord(accountName, fundMatcher.initialFundName, Currency(currency), amount, labels),
                    ImportParsedRecord(
                        accountName, fundMatcher.initialFundName, Currency(currency), amount.negate(), emptyList()
                    ),
                    ImportParsedRecord(accountName, fundMatcher.fundName, Currency(currency), amount, emptyList())
                )
            }

            is ByLabelWithTransfer -> {
                listOf(
                    ImportParsedRecord(accountName, fundMatcher.initialFundName, Currency(currency), amount, labels),
                    ImportParsedRecord(
                        accountName, fundMatcher.initialFundName, Currency(currency), amount.negate(), emptyList()
                    ),
                    ImportParsedRecord(accountName, fundMatcher.fundName, Currency(currency), amount, emptyList())
                )
            }
        }
    }

    private fun CsvRow.labels(): List<String> = getString(LABEL_COLUMN).labels()
    private fun String.labels(): List<String> = this.split(LABEL_DELIMITER).map { it.trim() }.filter { it.isNotBlank() }
}
