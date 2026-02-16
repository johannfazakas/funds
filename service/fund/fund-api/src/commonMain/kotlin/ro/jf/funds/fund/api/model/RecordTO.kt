package ro.jf.funds.fund.api.model

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.platform.api.model.Label
import ro.jf.funds.platform.api.serialization.BigDecimalSerializer
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
sealed class RecordTO {
    abstract val id: Uuid
    abstract val transactionId: Uuid
    abstract val dateTime: LocalDateTime
    abstract val accountId: Uuid
    abstract val fundId: Uuid
    abstract val amount: BigDecimal
    abstract val unit: FinancialUnit
    abstract val labels: List<Label>
    abstract val note: String?

    @Serializable
    @SerialName("CURRENCY")
    data class CurrencyRecord(
        @Serializable(with = UuidSerializer::class)
        override val id: Uuid,
        @Serializable(with = UuidSerializer::class)
        override val transactionId: Uuid,
        override val dateTime: LocalDateTime,
        @Serializable(with = UuidSerializer::class)
        override val accountId: Uuid,
        @Serializable(with = UuidSerializer::class)
        override val fundId: Uuid,
        @Serializable(with = BigDecimalSerializer::class)
        override val amount: BigDecimal,
        override val unit: Currency,
        override val labels: List<Label>,
        override val note: String? = null,
    ) : RecordTO()

    @Serializable
    @SerialName("INSTRUMENT")
    data class InstrumentRecord(
        @Serializable(with = UuidSerializer::class)
        override val id: Uuid,
        @Serializable(with = UuidSerializer::class)
        override val transactionId: Uuid,
        override val dateTime: LocalDateTime,
        @Serializable(with = UuidSerializer::class)
        override val accountId: Uuid,
        @Serializable(with = UuidSerializer::class)
        override val fundId: Uuid,
        @Serializable(with = BigDecimalSerializer::class)
        override val amount: BigDecimal,
        override val unit: Instrument,
        override val labels: List<Label>,
        override val note: String? = null,
    ) : RecordTO()
}
