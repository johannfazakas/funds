package ro.jf.funds.fund.service.domain

import kotlinx.datetime.LocalDateTime
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import ro.jf.funds.fund.api.model.FundTransactionType
import java.math.BigDecimal
import java.util.*

data class FundTransaction(
    val id: UUID,
    val userId: UUID,
    val type: FundTransactionType,
    val dateTime: LocalDateTime,
    val records: List<FundRecord>,
)

data class FundRecord(
    val id: UUID,
    val fundId: UUID,
    val accountId: UUID,
    val amount: BigDecimal,
    val unit: FinancialUnit,
    val labels: List<Label> = emptyList(),
)
