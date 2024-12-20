package ro.jf.funds.account.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class CreateAccountTransactionTO(
    val dateTime: LocalDateTime,
    val records: List<CreateAccountRecordTO>,
    // TODO(Johann) Expenses by fund - add a PropertyTO
    val properties: Map<String, List<String>> = emptyMap(),
)
