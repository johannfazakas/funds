package ro.jf.funds.importer.service.domain.service.parser

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
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
        val accountNamesByLabel = importConfiguration.accountMatchers.map { it.importLabel to it.accountName }.toMap()

        return rawImportItems
            .groupBy { it.transactionId() }
            .map { (transactionId, csvRows) ->
                // TODO(Johann) remove !! operators
                ImportTransaction(
                    transactionId = transactionId,
                    date = csvRows.first().let { LocalDateTime.parse(it[DATE_COLUMN]!!, dateTimeFormat) },
                    records = csvRows.map { csvRow ->
                        ImportRecord(
                            accountName = accountNamesByLabel[csvRow[ACCOUNT_LABEL_COLUMN]!!]!!,
                            currency = csvRow[CURRENCY_COLUMN]!!,
                            amount = csvRow[AMOUNT_COLUMN]!!.toBigDecimal()
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
