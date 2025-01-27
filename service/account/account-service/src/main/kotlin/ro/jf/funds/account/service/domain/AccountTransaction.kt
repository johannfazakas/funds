package ro.jf.funds.account.service.domain

import kotlinx.datetime.LocalDateTime
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import java.math.BigDecimal
import java.util.*

data class AccountTransaction(
    val id: UUID,
    val userId: UUID,
    val dateTime: LocalDateTime,
    val records: List<AccountRecord> = emptyList(),
    val properties: List<Property> = emptyList(),
)

data class AccountRecord(
    val id: UUID,
    val accountId: UUID,
    val amount: BigDecimal,
    val unit: FinancialUnit,
    val labels: List<Label> = emptyList(),
    val properties: List<Property> = emptyList(),
)
