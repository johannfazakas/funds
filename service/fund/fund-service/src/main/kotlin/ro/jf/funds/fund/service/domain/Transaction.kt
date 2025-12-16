package ro.jf.funds.fund.service.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Label
import ro.jf.funds.fund.api.model.TransactionType
import java.util.*

sealed class Transaction {
    abstract val id: UUID
    abstract val userId: UUID
    abstract val externalId: String
    abstract val dateTime: LocalDateTime
    abstract val type: TransactionType

    data class SingleRecord(
        override val id: UUID,
        override val userId: UUID,
        override val externalId: String,
        override val dateTime: LocalDateTime,
        val record: TransactionRecord,
    ) : Transaction() {
        override val type = TransactionType.SINGLE_RECORD
    }

    data class Transfer(
        override val id: UUID,
        override val userId: UUID,
        override val externalId: String,
        override val dateTime: LocalDateTime,
        val sourceRecord: TransactionRecord,
        val destinationRecord: TransactionRecord,
    ) : Transaction() {
        override val type = TransactionType.TRANSFER
    }

    data class Exchange(
        override val id: UUID,
        override val userId: UUID,
        override val externalId: String,
        override val dateTime: LocalDateTime,
        val sourceRecord: TransactionRecord,
        val destinationRecord: TransactionRecord,
        val feeRecord: TransactionRecord?,
    ) : Transaction() {
        override val type = TransactionType.EXCHANGE
    }

    data class OpenPosition(
        override val id: UUID,
        override val userId: UUID,
        override val externalId: String,
        override val dateTime: LocalDateTime,
        val currencyRecord: TransactionRecord,
        val instrumentRecord: TransactionRecord,
    ) : Transaction() {
        override val type = TransactionType.OPEN_POSITION
    }

    data class ClosePosition(
        override val id: UUID,
        override val userId: UUID,
        override val externalId: String,
        override val dateTime: LocalDateTime,
        val currencyRecord: TransactionRecord,
        val instrumentRecord: TransactionRecord,
    ) : Transaction() {
        override val type = TransactionType.CLOSE_POSITION
    }
}

data class TransactionRecord(
    val id: UUID,
    val accountId: UUID,
    val fundId: UUID,
    val amount: BigDecimal,
    val unit: FinancialUnit,
    val labels: List<Label> = emptyList(),
)
