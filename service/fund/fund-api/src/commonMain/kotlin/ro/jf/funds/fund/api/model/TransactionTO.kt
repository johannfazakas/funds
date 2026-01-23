package ro.jf.funds.fund.api.model

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.platform.api.model.Label
import ro.jf.funds.platform.api.serialization.BigDecimalSerializer
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
sealed class TransactionTO {
    abstract val id: Uuid
    abstract val userId: Uuid
    abstract val externalId: String
    abstract val dateTime: LocalDateTime

    abstract val type: TransactionType
    abstract val records: List<TransactionRecordTO>

    @Serializable
    @SerialName("SINGLE_RECORD")
    data class SingleRecord(
        @Serializable(with = UuidSerializer::class)
        override val id: Uuid,
        @Serializable(with = UuidSerializer::class)
        override val userId: Uuid,
        override val externalId: String,
        override val dateTime: LocalDateTime,
        val record: TransactionRecordTO.CurrencyRecord,
    ) : TransactionTO() {
        @Transient
        override val type = TransactionType.SINGLE_RECORD

        @Transient
        override val records: List<TransactionRecordTO> = listOf(record)
    }

    @Serializable
    @SerialName("TRANSFER")
    data class Transfer(
        @Serializable(with = UuidSerializer::class)
        override val id: Uuid,
        @Serializable(with = UuidSerializer::class)
        override val userId: Uuid,
        override val externalId: String,
        override val dateTime: LocalDateTime,
        val sourceRecord: TransactionRecordTO.CurrencyRecord,
        val destinationRecord: TransactionRecordTO.CurrencyRecord,
    ) : TransactionTO() {
        @Transient
        override val type = TransactionType.TRANSFER

        @Transient
        override val records: List<TransactionRecordTO> = listOf(sourceRecord, destinationRecord)
    }

    @Serializable
    @SerialName("EXCHANGE")
    data class Exchange(
        @Serializable(with = UuidSerializer::class)
        override val id: Uuid,
        @Serializable(with = UuidSerializer::class)
        override val userId: Uuid,
        override val externalId: String,
        override val dateTime: LocalDateTime,
        val sourceRecord: TransactionRecordTO.CurrencyRecord,
        val destinationRecord: TransactionRecordTO.CurrencyRecord,
        val feeRecord: TransactionRecordTO.CurrencyRecord?,
    ) : TransactionTO() {
        @Transient
        override val type = TransactionType.EXCHANGE

        @Transient
        override val records: List<TransactionRecordTO> = listOfNotNull(sourceRecord, destinationRecord, feeRecord)
    }

    @Serializable
    @SerialName("OPEN_POSITION")
    data class OpenPosition(
        @Serializable(with = UuidSerializer::class)
        override val id: Uuid,
        @Serializable(with = UuidSerializer::class)
        override val userId: Uuid,
        override val externalId: String,
        override val dateTime: LocalDateTime,
        val currencyRecord: TransactionRecordTO.CurrencyRecord,
        val instrumentRecord: TransactionRecordTO.InstrumentRecord,
    ) : TransactionTO() {
        @Transient
        override val type = TransactionType.OPEN_POSITION

        @Transient
        override val records: List<TransactionRecordTO> = listOf(currencyRecord, instrumentRecord)
    }

    @Serializable
    @SerialName("CLOSE_POSITION")
    data class ClosePosition(
        @Serializable(with = UuidSerializer::class)
        override val id: Uuid,
        @Serializable(with = UuidSerializer::class)
        override val userId: Uuid,
        override val externalId: String,
        override val dateTime: LocalDateTime,
        val currencyRecord: TransactionRecordTO.CurrencyRecord,
        val instrumentRecord: TransactionRecordTO.InstrumentRecord,
    ) : TransactionTO() {
        @Transient
        override val type = TransactionType.CLOSE_POSITION

        @Transient
        override val records: List<TransactionRecordTO> = listOf(currencyRecord, instrumentRecord)
    }
}

@Serializable
sealed class TransactionRecordTO {
    abstract val id: Uuid
    abstract val accountId: Uuid
    abstract val fundId: Uuid
    abstract val amount: BigDecimal
    abstract val unit: FinancialUnit
    abstract val labels: List<Label>

    @Serializable
    @SerialName("CURRENCY")
    data class CurrencyRecord(
        @Serializable(with = UuidSerializer::class)
        override val id: Uuid,
        @Serializable(with = UuidSerializer::class)
        override val accountId: Uuid,
        @Serializable(with = UuidSerializer::class)
        override val fundId: Uuid,
        @Serializable(with = BigDecimalSerializer::class)
        override val amount: BigDecimal,
        override val unit: Currency,
        override val labels: List<Label>,
    ) : TransactionRecordTO()

    @Serializable
    @SerialName("INSTRUMENT")
    data class InstrumentRecord(
        @Serializable(with = UuidSerializer::class)
        override val id: Uuid,
        @Serializable(with = UuidSerializer::class)
        override val accountId: Uuid,
        @Serializable(with = UuidSerializer::class)
        override val fundId: Uuid,
        @Serializable(with = BigDecimalSerializer::class)
        override val amount: BigDecimal,
        override val unit: Instrument,
        override val labels: List<Label>,
    ) : TransactionRecordTO()
}
