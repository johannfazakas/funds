package ro.jf.funds.importer.service.service.parser

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import ro.jf.funds.importer.api.model.FundMatcherTO
import ro.jf.funds.importer.api.model.FundMatcherTO.*
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.service.domain.*

private const val ACCOUNT_NAME_COLUMN = "account"
private const val AMOUNT_COLUMN = "amount"
private const val CURRENCY_COLUMN = "currency"
private const val LABEL_COLUMN = "labels"
private const val DATE_COLUMN = "date"
private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
private const val NOTE_COLUMN = "note"

class WalletCsvImportParser(
    private val csvParser: CsvParser
) : ImportParser {
    @OptIn(FormatStringsInDatetimeFormats::class)
    private val dateTimeFormat = LocalDateTime.Format { byUnicodePattern(DATE_FORMAT) }

    override fun parse(
        importConfiguration: ImportConfigurationTO, files: List<String>
    ): List<ImportTransaction> {
        return files
            .parse()
            .groupBy { it.transactionId() }
            .map { (transactionId, csvRows) -> toTransaction(importConfiguration, transactionId, csvRows) }
    }

    private fun List<String>.parse(): List<CsvRow> {
        val rawImportItems = this.map { csvParser.parse(it) }.flatten()
        if (rawImportItems.isEmpty())
            throw ImportDataException("No import data")
        return rawImportItems
    }

    private fun CsvRow.transactionId(): String {
        return listOf(
            this.getString(NOTE_COLUMN),
            this.getBigDecimal(AMOUNT_COLUMN).abs(),
            this.getString(DATE_COLUMN)
        ).hashCode().toString()
    }

    private fun toTransaction(
        importConfiguration: ImportConfigurationTO,
        transactionId: String,
        csvRows: List<CsvRow>
    ): ImportTransaction {
        return ImportTransaction(
            transactionId = transactionId,
            dateTime = csvRows.first().getDateTime(DATE_COLUMN, dateTimeFormat),
            records = csvRows.flatMap { csvRow -> toImportRecords(importConfiguration, csvRow) }
        )
    }

    private fun toImportRecords(importConfiguration: ImportConfigurationTO, csvRow: CsvRow): List<ImportRecord> {
        val importAccountName = csvRow.getString(ACCOUNT_NAME_COLUMN)
        val accountName = importConfiguration.accountMatchers.getAccountName(importAccountName)
        val importType = csvRow.getString(LABEL_COLUMN)
        val currency = csvRow.getString(CURRENCY_COLUMN)
        val amount = csvRow.getBigDecimal(AMOUNT_COLUMN)
        val fundMatcher = importConfiguration.fundMatchers.getFundMatcher(importAccountName, importType)

        return when (fundMatcher) {
            is ByAccount, is ByLabel, is ByAccountLabel ->
                listOf(ImportRecord(accountName, fundMatcher.fundName, currency, amount))

            is ByAccountLabelWithTransfer -> {
                listOf(
                    ImportRecord(accountName, fundMatcher.initialFundName, currency, amount),
                    ImportRecord(accountName, fundMatcher.initialFundName, currency, amount.negate()),
                    ImportRecord(accountName, fundMatcher.fundName, currency, amount)
                )
            }
        }
    }
}
