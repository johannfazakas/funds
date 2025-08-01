package ro.jf.funds.account.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.util.*

@Serializable
data class AccountTransactionTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val externalId: String,
    val dateTime: LocalDateTime,
    val records: List<AccountRecordTO>,
    val properties: List<PropertyTO>,
)
