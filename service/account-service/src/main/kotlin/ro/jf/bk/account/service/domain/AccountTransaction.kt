package ro.jf.bk.account.service.domain

import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal
import java.util.*

data class AccountTransaction(
    val id: UUID,
    val userId: UUID,
    val dateTime: LocalDateTime,
    val records: List<AccountRecord>,
    val metadata: Map<String, String>
)

data class AccountRecord(
    val id: UUID,
    val accountId: UUID,
    val amount: BigDecimal,
    val metadata: Map<String, String>
)
