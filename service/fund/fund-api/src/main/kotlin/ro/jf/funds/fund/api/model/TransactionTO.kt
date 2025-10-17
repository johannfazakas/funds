package ro.jf.funds.fund.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.serialization.BigDecimalSerializer
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.math.BigDecimal
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class TransactionTO {
    abstract val id: UUID
    abstract val userId: UUID
    abstract val externalId: String
    abstract val dateTime: LocalDateTime

    abstract val type: TransactionType
    abstract val records: List<TransactionRecordTO>

    @Serializable
    @SerialName("SINGLE_RECORD")
    data class SingleRecord(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        @Serializable(with = UUIDSerializer::class)
        override val userId: UUID,
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
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        @Serializable(with = UUIDSerializer::class)
        override val userId: UUID,
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
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        @Serializable(with = UUIDSerializer::class)
        override val userId: UUID,
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
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        @Serializable(with = UUIDSerializer::class)
        override val userId: UUID,
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
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        @Serializable(with = UUIDSerializer::class)
        override val userId: UUID,
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
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val accountId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val fundId: UUID,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val unit: FinancialUnit,
    val labels: List<Label>,
)
