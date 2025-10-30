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
import ro.jf.funds.commons.serialization.LocalDateTimeSerializer
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.math.BigDecimal
import java.util.*

@Serializable
data class CreateTransactionsTO(
    val transactions: List<CreateTransactionTO>,
)

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class CreateTransactionTO {
    @Serializable(with = LocalDateTimeSerializer::class)
    abstract val dateTime: LocalDateTime
    abstract val externalId: String
    abstract val type: TransactionType
    abstract val records: List<CreateTransactionRecordTO>

    @Serializable
    @SerialName("SINGLE_RECORD")
    data class SingleRecord(
        @Serializable(with = LocalDateTimeSerializer::class)
        override val dateTime: LocalDateTime,
        override val externalId: String,
        val record: CreateTransactionRecordTO,
    ) : CreateTransactionTO() {
        @Transient
        override val type = TransactionType.SINGLE_RECORD

        @Transient
        override val records: List<CreateTransactionRecordTO> = listOf(record)
    }

    @Serializable
    @SerialName("TRANSFER")
    data class Transfer(
        @Serializable(with = LocalDateTimeSerializer::class)
        override val dateTime: LocalDateTime,
        override val externalId: String,
        val sourceRecord: CreateTransactionRecordTO,
        val destinationRecord: CreateTransactionRecordTO,
    ) : CreateTransactionTO() {
        @Transient
        override val type = TransactionType.TRANSFER

        @Transient
        override val records: List<CreateTransactionRecordTO> = listOf(sourceRecord, destinationRecord)
    }

    @Serializable
    @SerialName("EXCHANGE")
    data class Exchange(
        @Serializable(with = LocalDateTimeSerializer::class)
        override val dateTime: LocalDateTime,
        override val externalId: String,
        val sourceRecord: CreateTransactionRecordTO,
        val destinationRecord: CreateTransactionRecordTO,
        val feeRecord: CreateTransactionRecordTO?,
    ) : CreateTransactionTO() {
        @Transient
        override val type = TransactionType.EXCHANGE

        @Transient
        override val records: List<CreateTransactionRecordTO> = listOfNotNull(sourceRecord, destinationRecord, feeRecord)
    }

    @Serializable
    @SerialName("OPEN_POSITION")
    data class OpenPosition(
        @Serializable(with = LocalDateTimeSerializer::class)
        override val dateTime: LocalDateTime,
        override val externalId: String,
        val currencyRecord: CreateTransactionRecordTO,
        val instrumentRecord: CreateTransactionRecordTO,
    ) : CreateTransactionTO() {
        @Transient
        override val type = TransactionType.OPEN_POSITION

        @Transient
        override val records: List<CreateTransactionRecordTO> = listOf(currencyRecord, instrumentRecord)
    }

    @Serializable
    @SerialName("CLOSE_POSITION")
    data class ClosePosition(
        @Serializable(with = LocalDateTimeSerializer::class)
        override val dateTime: LocalDateTime,
        override val externalId: String,
        val currencyRecord: CreateTransactionRecordTO,
        val instrumentRecord: CreateTransactionRecordTO,
    ) : CreateTransactionTO() {
        @Transient
        override val type = TransactionType.CLOSE_POSITION

        @Transient
        override val records: List<CreateTransactionRecordTO> = listOf(currencyRecord, instrumentRecord)
    }
}

@Serializable
data class CreateTransactionRecordTO(
    @Serializable(with = UUIDSerializer::class)
    val accountId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val fundId: UUID,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val unit: FinancialUnit,
    val labels: List<Label> = emptyList(),
)
