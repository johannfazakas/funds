package ro.jf.funds.fund.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.serialization.BigDecimalSerializer
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.math.BigDecimal
import java.util.*

@Serializable
data class CreateFundTransactionsTO(
    val transactions: List<CreateFundTransactionTO>,
)

@Serializable
data class CreateFundTransactionTO(
    val dateTime: LocalDateTime,
    val records: List<CreateFundRecordTO>,
)

@Serializable
data class CreateFundRecordTO(
    @Serializable(with = UUIDSerializer::class)
    val fundId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val accountId: UUID,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val unit: FinancialUnit,
    val labels: List<Label> = emptyList(),
)
