package ro.jf.funds.importer.service.service.parser

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import ro.jf.funds.platform.api.model.FinancialUnit
import java.util.*

class FundsFormatImportItem(private val csvRow: CsvRow) : ImportItem() {
    override val dateTime: LocalDateTime by lazy { LocalDateTime(csvRow.getDate(DATE_COLUMN, dateFormat), LocalTime(0, 0)) }
    override val accountName: String by lazy { csvRow.getString(ACCOUNT_COLUMN) }
    override val amount: BigDecimal by lazy { csvRow.getBigDecimal(AMOUNT_COLUMN) }
    override val unit: FinancialUnit by lazy { FinancialUnit.of(csvRow.getString(UNIT_TYPE_COLUMN), csvRow.getString(UNIT_COLUMN)) }
    override val note: String by lazy { csvRow.getString(NOTE_COLUMN) }
    override val labels: List<String> by lazy { listOfNotNull(csvRow.getString(LABEL_COLUMN).takeIf { it.isNotBlank() }) }

    override fun transactionId(isExchange: (ImportItem) -> Boolean): String {
        val baseId = listOf(note, dateTime.date.toString()).joinToString()
        return UUID.nameUUIDFromBytes(baseId.toByteArray()).toString()
    }

    companion object {
        private const val DATE_COLUMN = "date"
        private const val ACCOUNT_COLUMN = "account"
        private const val AMOUNT_COLUMN = "amount"
        private const val UNIT_COLUMN = "unit"
        private const val UNIT_TYPE_COLUMN = "unit_type"
        private const val NOTE_COLUMN = "note"
        private const val LABEL_COLUMN = "label"
        private const val DATE_FORMAT = "yyyy-MM-dd"

        @OptIn(FormatStringsInDatetimeFormats::class)
        private val dateFormat = LocalDate.Format { byUnicodePattern(DATE_FORMAT) }
    }
}
