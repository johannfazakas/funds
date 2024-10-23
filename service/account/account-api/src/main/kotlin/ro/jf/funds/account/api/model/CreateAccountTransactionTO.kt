package ro.jf.funds.account.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class CreateAccountTransactionsTO(
    val transactions: List<CreateAccountTransactionTO>,
)

@Serializable
data class CreateAccountTransactionTO(
    val dateTime: LocalDateTime,
    val records: List<CreateAccountRecordTO>,
    val metadata: Map<String, String> = emptyMap(),
)

