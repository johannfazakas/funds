package ro.jf.bk.account.service.domain.command

import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal
import java.util.*

data class CreateTransactionCommand(
    val userId: UUID,
    val dateTime: LocalDateTime,
    val records: List<CreateRecordCommand>,
    val metadata: Map<String, String> = emptyMap()
)

data class CreateRecordCommand(
    val accountId: UUID,
    val amount: BigDecimal,
    val metadata: Map<String, String> = emptyMap()
)
