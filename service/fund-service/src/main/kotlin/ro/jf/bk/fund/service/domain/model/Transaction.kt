package ro.jf.bk.fund.service.domain.model

import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal
import java.util.*

data class Transaction(
    val id: UUID,
    val userId: UUID,
    val dateTime: LocalDateTime,
    val records: List<Record>
)

data class Record(
    val id: UUID,
    val fundId: UUID,
    val accountId: UUID,
    val amount: BigDecimal
)
