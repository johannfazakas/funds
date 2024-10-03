package ro.jf.funds.importer.service.domain.service.parser

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import ro.jf.funds.importer.service.domain.model.ImportItem

private const val ACCOUNT_ALIAS_COLUMN = "account"
private const val AMOUNT_COLUMN = "amount"
private const val CURRENCY_COLUMN = "currency"
private const val DATE_COLUMN = "date"
private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"

class WalletCsvImportParser(
    private val csvParser: CsvParser
) : ImportParser {
    @OptIn(FormatStringsInDatetimeFormats::class)
    private val dateTimeFormat = LocalDateTime.Format { byUnicodePattern(DATE_FORMAT) }

    override fun parse(files: List<String>): List<ImportItem> {
        val rawImportItems = files.map { csvParser.parse(it) }.flatten()

        return rawImportItems
            .map {
                ImportItem(
                    amount = it[AMOUNT_COLUMN]!!.toBigDecimal(),
                    currency = it[CURRENCY_COLUMN]!!,
                    accountName = it[ACCOUNT_ALIAS_COLUMN]!!,
                    date = LocalDateTime.parse(it[DATE_COLUMN]!!, dateTimeFormat),
                    transactionId = it[DATE_COLUMN]!!
                )
            }
    }
}
