package ro.jf.funds.importer.service.domain.service.parser

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import ro.jf.funds.importer.service.domain.model.ImportConfiguration
import ro.jf.funds.importer.service.domain.model.ImportItem

private const val ACCOUNT_LABEL_COLUMN = "account"
private const val AMOUNT_COLUMN = "amount"
private const val CURRENCY_COLUMN = "currency"
private const val DATE_COLUMN = "date"
private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"

class WalletCsvImportParser(
    private val csvParser: CsvParser
) : ImportParser {
    @OptIn(FormatStringsInDatetimeFormats::class)
    private val dateTimeFormat = LocalDateTime.Format { byUnicodePattern(DATE_FORMAT) }

    override fun parse(importConfiguration: ImportConfiguration, files: List<String>): List<ImportItem> {
        val rawImportItems = files.map { csvParser.parse(it) }.flatten()
        val accountNamesByLabel = importConfiguration.accountMatchers.map { it.importLabel to it.accountName }.toMap()

        return rawImportItems
            .map { csvRow ->
                ImportItem(
                    // TODO(Johann) remove !! operators
                    amount = csvRow[AMOUNT_COLUMN]!!.toBigDecimal(),
                    currency = csvRow[CURRENCY_COLUMN]!!,
                    accountName = accountNamesByLabel[csvRow[ACCOUNT_LABEL_COLUMN]!!]!!,
                    date = LocalDateTime.parse(csvRow[DATE_COLUMN]!!, dateTimeFormat),
                    transactionId = csvRow[DATE_COLUMN]!!
                )
            }
    }
}
