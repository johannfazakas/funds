package ro.jf.bk.fund.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ro.jf.bk.commons.serialization.BigDecimalSerializer
import ro.jf.bk.commons.serialization.UUIDSerializer
import java.math.BigDecimal
import java.util.*

@Serializable
data class CreateTransactionTO(
    val dateTime: LocalDateTime,
    val records: List<CreateRecordTO>
)

@Serializable
data class CreateRecordTO(
    @Serializable(with = UUIDSerializer::class)
    val fundId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val accountId: UUID,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal
)
