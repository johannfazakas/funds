package ro.jf.bk.account.service.domain.model

import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal
import java.util.*

data class Transaction(
    val id: UUID,
    val userId: UUID,
    val dateTime: LocalDateTime,
    val records: List<Record>,
    val metadata: Map<String, String>
)

data class Record(
    val id: UUID,
    val accountId: UUID,
    val amount: BigDecimal,
    val metadata: Map<String, String>
)
