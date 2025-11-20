package ro.jf.funds.fund.api.model

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator
import ro.jf.funds.commons.api.model.FinancialUnit
import ro.jf.funds.commons.api.model.Label
import ro.jf.funds.commons.api.serialization.BigDecimalSerializer
import ro.jf.funds.commons.api.serialization.UuidSerializer

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
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
        val record: TransactionRecordTO,
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
        val sourceRecord: TransactionRecordTO,
        val destinationRecord: TransactionRecordTO,
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
        val sourceRecord: TransactionRecordTO,
        val destinationRecord: TransactionRecordTO,
        val feeRecord: TransactionRecordTO?,
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
        val currencyRecord: TransactionRecordTO,
        val instrumentRecord: TransactionRecordTO,
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
        val currencyRecord: TransactionRecordTO,
        val instrumentRecord: TransactionRecordTO,
    ) : TransactionTO() {
        @Transient
        override val type = TransactionType.CLOSE_POSITION

        @Transient
        override val records: List<TransactionRecordTO> = listOf(currencyRecord, instrumentRecord)
    }
}

@Serializable
data class TransactionRecordTO(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    @Serializable(with = UuidSerializer::class)
    val accountId: Uuid,
    @Serializable(with = UuidSerializer::class)
    val fundId: Uuid,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val unit: FinancialUnit,
    val labels: List<Label>,
)
