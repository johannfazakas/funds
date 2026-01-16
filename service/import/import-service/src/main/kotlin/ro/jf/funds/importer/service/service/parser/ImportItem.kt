package ro.jf.funds.importer.service.service.parser

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import ro.jf.funds.platform.api.model.FinancialUnit
import java.util.*

abstract class ImportItem {
    abstract val dateTime: LocalDateTime
    abstract val accountName: String
    abstract val amount: BigDecimal
    abstract val unit: FinancialUnit
    abstract val labels: List<String>
    abstract val note: String

    fun transactionId(isExchange: (ImportItem) -> Boolean): String {
        val baseId = if (isExchange(this)) {
            listOf(dateTime.inWholeMinutes()).joinToString()
        } else {
            listOf(note, dateTime.date.toString()).joinToString()
        }
        return UUID.nameUUIDFromBytes(baseId.toByteArray()).toString()
    }

    private fun LocalDateTime.inWholeMinutes(): Long = this.toInstant(TimeZone.UTC).epochSeconds / 60
}
