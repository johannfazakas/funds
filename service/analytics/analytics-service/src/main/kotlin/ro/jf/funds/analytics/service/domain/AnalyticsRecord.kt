package ro.jf.funds.analytics.service.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Label
import java.util.*

data class AnalyticsRecord(
    val id: UUID,
    val userId: UUID,
    val fundId: UUID,
    val accountId: UUID,
    val transactionId: UUID,
    val transactionType: TransactionType,
    val dateTime: LocalDateTime,
    val amount: BigDecimal,
    val unit: FinancialUnit,
    val labels: List<Label>,
)
