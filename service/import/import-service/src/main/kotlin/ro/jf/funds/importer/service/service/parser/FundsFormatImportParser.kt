package ro.jf.funds.importer.service.service.parser

import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.format.byUnicodePattern
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import ro.jf.funds.importer.api.model.ExchangeMatcherTO
import ro.jf.funds.importer.api.model.FundMatcherTO.*
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.service.domain.*
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import java.util.*

private const val DATE_COLUMN = "date"
private const val ACCOUNT_COLUMN = "account"
private const val AMOUNT_COLUMN = "amount"
private const val UNIT_COLUMN = "unit"
private const val UNIT_TYPE_COLUMN = "unit_type"
private const val NOTE_COLUMN = "note"
private const val LABEL_COLUMN = "label"
private const val DATE_FORMAT = "yyyy-MM-dd"

class FundsFormatImportParser(
    private val csvParser: CsvParser,
) : ImportParser {
    // TODO(Johann) update this, it is a warning
    private val dateFormat = LocalDate.Format { byUnicodePattern(DATE_FORMAT) }

    override fun parse(
        importConfiguration: ImportConfigurationTO,
        files: List<String>,
    ): List<ImportParsedTransaction> {
        return files
            .parse()
            .groupBy { it.transactionId(importConfiguration.exchangeMatchers) }
            .mapNotNull { (transactionId, csvRows) -> toTransaction(importConfiguration, transactionId, csvRows) }
    }

    private fun List<String>.parse(): List<CsvRow> {
        val rawImportItems = this.map { csvParser.parse(it) }.flatten()
        if (rawImportItems.isEmpty())
            throw ImportDataException("No import reportdata")
        return rawImportItems
    }

    private fun CsvRow.transactionId(exchangeMatchers: List<ExchangeMatcherTO>): String {
        return listOf(this.getString(DATE_COLUMN), this.getString(NOTE_COLUMN))
            .joinToString("_")
            .let { UUID.nameUUIDFromBytes(it.toByteArray()).toString() }
    }

    private fun toTransaction(
        importConfiguration: ImportConfigurationTO,
        transactionId: String,
        csvRows: List<CsvRow>,
    ): ImportParsedTransaction? {
        val records = toImportRecords(importConfiguration, csvRows)
        if (records == null) {
            return null
        }
        return ImportParsedTransaction(
            transactionExternalId = transactionId,
            dateTime = csvRows.minOf { it.getDate(DATE_COLUMN, dateFormat) }.atTime(0, 0),
            records = records
        )
    }

    private fun toImportRecords(
        importConfiguration: ImportConfigurationTO,
        csvRows: List<CsvRow>,
    ): List<ImportParsedRecord>? {
        val parsedRecords = csvRows.map { toImportRecords(importConfiguration, it) }
        val mappedParsedRecords = parsedRecords.filterNotNull()
        return if (mappedParsedRecords.size < parsedRecords.size)
            null
        else
            mappedParsedRecords.flatten()
    }

    private fun toImportRecords(importConfiguration: ImportConfigurationTO, csvRow: CsvRow): List<ImportParsedRecord>? {
        // TODO(Johann) seems like logic could partially be extracted
        val importAccountName = csvRow.getString(ACCOUNT_COLUMN)
        val accountName = importConfiguration.accountMatchers.getAccountName(importAccountName)
        if (accountName == null) return null
        val importLabels = listOfNotNull(csvRow.getString(LABEL_COLUMN).takeIf { it.isNotBlank() })
        val unit = FinancialUnit.of(csvRow.getString(UNIT_TYPE_COLUMN), csvRow.getString(UNIT_COLUMN))
        val amount = csvRow.getBigDecimal(AMOUNT_COLUMN)
        val fundMatcher = importConfiguration.fundMatchers.getFundMatcher(importAccountName, importLabels)
        val labelMatchers = importConfiguration.labelMatchers.getLabelMatchers(importLabels)
        val labels = labelMatchers.map { it.label }

        return when (fundMatcher) {
            is ByAccount, is ByLabel, is ByAccountLabel ->
                listOf(ImportParsedRecord(accountName, fundMatcher.fundName, unit, amount, labels))

            is ByAccountLabelWithPostTransfer -> {
                listOf(
                    ImportParsedRecord(accountName, fundMatcher.initialFundName, unit, amount, labels),
                    ImportParsedRecord(
                        accountName, fundMatcher.initialFundName, unit, amount.negate(), emptyList()
                    ),
                    ImportParsedRecord(accountName, fundMatcher.fundName, unit, amount, emptyList())
                )
            }

            is ByAccountLabelWithPreTransfer -> {
                listOf(
                    ImportParsedRecord(
                        accountName, fundMatcher.initialFundName, unit, amount, emptyList()
                    ),
                    ImportParsedRecord(accountName, fundMatcher.fundName, unit, amount.negate(), emptyList()),
                    ImportParsedRecord(accountName, fundMatcher.fundName, unit, amount, labels),
                )
            }

            is ByLabelWithPostTransfer -> {
                listOf(
                    ImportParsedRecord(accountName, fundMatcher.initialFundName, unit, amount, labels),
                    ImportParsedRecord(
                        accountName, fundMatcher.initialFundName, unit, amount.negate(), emptyList()
                    ),
                    ImportParsedRecord(accountName, fundMatcher.fundName, unit, amount, emptyList())
                )
            }
        }
    }

    private fun CsvRow.label(): Label? = getString(LABEL_COLUMN).takeIf { it.isNotBlank() }?.let(::Label)
}
