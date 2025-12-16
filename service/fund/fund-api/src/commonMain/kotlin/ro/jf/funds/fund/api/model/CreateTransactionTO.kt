package ro.jf.funds.fund.api.model

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Label
import ro.jf.funds.platform.api.serialization.BigDecimalSerializer
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
data class CreateTransactionsTO(
    val transactions: List<CreateTransactionTO>,
)

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class CreateTransactionTO {
    abstract val dateTime: LocalDateTime
    abstract val externalId: String
    abstract val type: TransactionType
    abstract val records: List<CreateTransactionRecordTO>

    @Serializable
    @SerialName("SINGLE_RECORD")
    data class SingleRecord(
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
    @Serializable(with = UuidSerializer::class)
    val accountId: Uuid,
    @Serializable(with = UuidSerializer::class)
    val fundId: Uuid,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val unit: FinancialUnit,
    val labels: List<Label> = emptyList(),
)
