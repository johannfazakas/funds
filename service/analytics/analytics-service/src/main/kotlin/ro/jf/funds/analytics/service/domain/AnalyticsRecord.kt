package ro.jf.funds.analytics.service.domain

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.Category
import ro.jf.funds.platform.api.model.FinancialUnit

data class AnalyticsRecord(
    val id: Uuid,
    val userId: Uuid,
    val fundId: Uuid,
    val accountId: Uuid,
    val transactionId: Uuid,
    val transactionType: TransactionType,
    val dateTime: LocalDateTime,
    val amount: BigDecimal,
    val unit: FinancialUnit,
    val category: Category?,
)
