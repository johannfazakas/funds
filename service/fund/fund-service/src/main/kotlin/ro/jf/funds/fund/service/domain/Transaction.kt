package ro.jf.funds.fund.service.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Instrument
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
        val record: TransactionRecord.CurrencyRecord,
    ) : Transaction() {
        override val type = TransactionType.SINGLE_RECORD
    }

    data class Transfer(
        override val id: UUID,
        override val userId: UUID,
        override val externalId: String,
        override val dateTime: LocalDateTime,
        val sourceRecord: TransactionRecord.CurrencyRecord,
        val destinationRecord: TransactionRecord.CurrencyRecord,
    ) : Transaction() {
        override val type = TransactionType.TRANSFER
    }

    data class Exchange(
        override val id: UUID,
        override val userId: UUID,
        override val externalId: String,
        override val dateTime: LocalDateTime,
        val sourceRecord: TransactionRecord.CurrencyRecord,
        val destinationRecord: TransactionRecord.CurrencyRecord,
        val feeRecord: TransactionRecord.CurrencyRecord?,
    ) : Transaction() {
        override val type = TransactionType.EXCHANGE
    }

    data class OpenPosition(
        override val id: UUID,
        override val userId: UUID,
        override val externalId: String,
        override val dateTime: LocalDateTime,
        val currencyRecord: TransactionRecord.CurrencyRecord,
        val instrumentRecord: TransactionRecord.InstrumentRecord,
    ) : Transaction() {
        override val type = TransactionType.OPEN_POSITION
    }

    data class ClosePosition(
        override val id: UUID,
        override val userId: UUID,
        override val externalId: String,
        override val dateTime: LocalDateTime,
        val currencyRecord: TransactionRecord.CurrencyRecord,
        val instrumentRecord: TransactionRecord.InstrumentRecord,
    ) : Transaction() {
        override val type = TransactionType.CLOSE_POSITION
    }
}

sealed class TransactionRecord {
    abstract val id: UUID
    abstract val accountId: UUID
    abstract val fundId: UUID
    abstract val amount: BigDecimal
    abstract val unit: FinancialUnit
    abstract val labels: List<Label>
    abstract val note: String?

    data class CurrencyRecord(
        override val id: UUID,
        override val accountId: UUID,
        override val fundId: UUID,
        override val amount: BigDecimal,
        override val unit: Currency,
        override val labels: List<Label> = emptyList(),
        override val note: String? = null,
    ) : TransactionRecord()

    data class InstrumentRecord(
        override val id: UUID,
        override val accountId: UUID,
        override val fundId: UUID,
        override val amount: BigDecimal,
        override val unit: Instrument,
        override val labels: List<Label> = emptyList(),
        override val note: String? = null,
    ) : TransactionRecord()
}
