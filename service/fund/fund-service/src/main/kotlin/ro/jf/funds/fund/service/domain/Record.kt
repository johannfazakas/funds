package ro.jf.funds.fund.service.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.platform.api.model.Label
import java.util.*

sealed class Record {
    abstract val id: UUID
    abstract val transactionId: UUID
    abstract val dateTime: LocalDateTime
    abstract val accountId: UUID
    abstract val fundId: UUID
    abstract val amount: BigDecimal
    abstract val unit: FinancialUnit
    abstract val labels: List<Label>
    abstract val note: String?

    data class CurrencyRecord(
        override val id: UUID,
        override val transactionId: UUID,
        override val dateTime: LocalDateTime,
        override val accountId: UUID,
        override val fundId: UUID,
        override val amount: BigDecimal,
        override val unit: Currency,
        override val labels: List<Label> = emptyList(),
        override val note: String? = null,
    ) : Record()

    data class InstrumentRecord(
        override val id: UUID,
        override val transactionId: UUID,
        override val dateTime: LocalDateTime,
        override val accountId: UUID,
        override val fundId: UUID,
        override val amount: BigDecimal,
        override val unit: Instrument,
        override val labels: List<Label> = emptyList(),
        override val note: String? = null,
    ) : Record()
}
