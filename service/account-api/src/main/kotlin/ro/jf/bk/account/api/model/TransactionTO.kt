package ro.jf.bk.account.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ro.jf.bk.commons.serialization.UUIDSerializer
import java.util.*

@Serializable
data class TransactionTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val dateTime: LocalDateTime,
    val records: List<RecordTO>,
    val metadata: Map<String, String>
)
