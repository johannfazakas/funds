package ro.jf.funds.account.service.domain

import kotlinx.datetime.LocalDateTime
import ro.jf.funds.commons.model.FinancialUnit
import java.math.BigDecimal
import java.util.*

data class AccountTransaction(
    val id: UUID,
    val userId: UUID,
    val dateTime: LocalDateTime,
    val records: List<AccountRecord>,
    val properties: List<Property>,
)

data class AccountRecord(
    val id: UUID,
    val accountId: UUID,
    val amount: BigDecimal,
    val unit: FinancialUnit,
    val properties: List<Property>,
)
