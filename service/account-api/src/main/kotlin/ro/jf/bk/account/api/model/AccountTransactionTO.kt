package ro.jf.bk.account.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ro.jf.bk.commons.serialization.UUIDSerializer
import java.util.*

@Serializable
data class AccountTransactionTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val dateTime: LocalDateTime,
    val records: List<AccountRecordTO>,
    val metadata: Map<String, String>
)
