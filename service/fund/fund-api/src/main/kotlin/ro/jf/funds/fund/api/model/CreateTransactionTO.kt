package ro.jf.funds.fund.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.serialization.BigDecimalSerializer
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.math.BigDecimal
import java.util.UUID

@Serializable
data class CreateTransactionsTO(
    val transactions: List<CreateTransactionTO>,
)

@Serializable
data class CreateTransactionTO(
    val dateTime: LocalDateTime,
    val externalId: String,
    val type: TransactionType,
    val records: List<CreateTransactionRecord>,
)

@Serializable
data class CreateTransactionRecord(
    @Serializable(with = UUIDSerializer::class)
    val accountId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val fundId: UUID,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val unit: FinancialUnit,
    val labels: List<Label> = emptyList(),
)