package ro.jf.funds.importer.service.service.parser

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.platform.api.model.FinancialUnit

abstract class ImportItem {
    abstract val dateTime: LocalDateTime
    abstract val accountName: String
    abstract val amount: BigDecimal
    abstract val unit: FinancialUnit
    abstract val labels: List<String>
    abstract val note: String

    abstract fun transactionId(isExchange: (ImportItem) -> Boolean): String
}
