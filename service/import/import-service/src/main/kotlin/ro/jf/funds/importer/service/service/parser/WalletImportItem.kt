package ro.jf.funds.importer.service.service.parser

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import ro.jf.funds.platform.api.model.Currency

class WalletImportItem(private val csvRow: CsvRow) : ImportItem() {
    override val dateTime: LocalDateTime by lazy { csvRow.getDateTime(DATE_COLUMN, dateTimeFormat) }
    override val accountName: String by lazy { csvRow.getString(ACCOUNT_NAME_COLUMN) }
    override val amount: BigDecimal by lazy { csvRow.getBigDecimal(AMOUNT_COLUMN) }
    override val unit: Currency by lazy { Currency(csvRow.getString(CURRENCY_COLUMN)) }
    override val labels: List<String> by lazy {
        csvRow.getString(LABEL_COLUMN)
            .split(LABEL_DELIMITER)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    override val note: String by lazy { csvRow.getString(NOTE_COLUMN) }

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
