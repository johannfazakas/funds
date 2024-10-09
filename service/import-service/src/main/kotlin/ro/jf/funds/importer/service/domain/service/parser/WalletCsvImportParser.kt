package ro.jf.funds.importer.service.domain.service.parser

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.domain.model.ImportConfiguration
import ro.jf.funds.importer.service.domain.model.ImportRecord
import ro.jf.funds.importer.service.domain.model.ImportTransaction

private const val ACCOUNT_LABEL_COLUMN = "account"
private const val AMOUNT_COLUMN = "amount"
private const val CURRENCY_COLUMN = "currency"
private const val DATE_COLUMN = "date"
private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
private const val NOTE_COLUMN = "note"

class WalletCsvImportParser(
    private val csvParser: CsvParser
) : ImportParser {
    @OptIn(FormatStringsInDatetimeFormats::class)
    private val dateTimeFormat = LocalDateTime.Format { byUnicodePattern(DATE_FORMAT) }

    override fun parse(importConfiguration: ImportConfiguration, files: List<String>): List<ImportTransaction> {
        val rawImportItems = files.map { csvParser.parse(it) }.flatten()
        // TODO(Johann) raise exception if empty raw import items
        // TODO(Johann) could move account names by label recognition on a domain class
        val accountNamesByLabel =
            importConfiguration.accountMatchers.map { it.importAccountName to it.accountName }.toMap()

        return rawImportItems
            .groupBy { it.transactionId() }
            .map { (transactionId, csvRows) ->
                ImportTransaction(
                    transactionId = transactionId,
                    date = csvRows.first().getDateTime(DATE_COLUMN, dateTimeFormat),
                    records = csvRows.map { csvRow ->
                        ImportRecord(
                            accountName = csvRow.getString(ACCOUNT_LABEL_COLUMN).let {
                                accountNamesByLabel[it] ?: throw ImportDataException(
                                    "Account name not matched: ${csvRow.getString(ACCOUNT_LABEL_COLUMN)}"
                                )
                            },
                            currency = csvRow.getString(CURRENCY_COLUMN),
                            amount = csvRow.getBigDecimal(AMOUNT_COLUMN)
                        )
                    }
                )
            }
    }

    private fun CsvRow.transactionId(): String {
        return listOf(
            this[NOTE_COLUMN]!!,
            this[AMOUNT_COLUMN]!!.toBigDecimal().abs(),
            this[DATE_COLUMN]!!
        ).hashCode().toString()
    }
}
