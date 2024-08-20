package ro.jf.bk.account.api.model

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class CreateTransactionTO(
    val dateTime: LocalDateTime,
    val records: List<CreateRecordTO>,
    val metadata: Map<String, String> = emptyMap(),
)

data class CreateRecordTO(
    val accountId: UUID,
    val amount: BigDecimal,
    val metadata: Map<String, String> = emptyMap(),
)
