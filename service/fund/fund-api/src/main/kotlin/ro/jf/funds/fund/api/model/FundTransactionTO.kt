package ro.jf.funds.fund.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.util.*

@Serializable
data class FundTransactionTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val type: FundTransactionType,
    val dateTime: LocalDateTime,
    val records: List<FundRecordTO>,
)
