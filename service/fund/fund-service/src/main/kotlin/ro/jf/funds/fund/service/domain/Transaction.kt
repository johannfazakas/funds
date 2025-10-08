package ro.jf.funds.fund.service.domain

import kotlinx.datetime.LocalDateTime
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import java.math.BigDecimal
import java.util.*

data class Transaction(
    val id: UUID,
    val userId: UUID,
    val externalId: String,
    val type: TransactionType,
    val dateTime: LocalDateTime,
    val records: List<TransactionRecord> = emptyList(),
)

data class TransactionRecord(
    val id: UUID,
    val accountId: UUID,
    val fundId: UUID,
    val amount: BigDecimal,
    val unit: FinancialUnit,
    val labels: List<Label> = emptyList(),
)
