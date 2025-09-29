package ro.jf.funds.account.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class CreateAccountTransactionTO(
    val dateTime: LocalDateTime,
    val externalId: String,
    val type: AccountTransactionType,
    val records: List<CreateAccountRecordTO>,
    val properties: List<PropertyTO> = emptyList(),
)
