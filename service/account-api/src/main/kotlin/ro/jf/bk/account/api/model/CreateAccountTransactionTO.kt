package ro.jf.bk.account.api.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ro.jf.bk.commons.serialization.BigDecimalSerializer
import ro.jf.bk.commons.serialization.UUIDSerializer
import java.math.BigDecimal
import java.util.*

@Serializable
data class CreateAccountTransactionTO(
    val dateTime: LocalDateTime,
    val records: List<CreateAccountRecordTO>,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class CreateAccountRecordTO(
    @Serializable(with = UUIDSerializer::class)
    val accountId: UUID,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val metadata: Map<String, String> = emptyMap(),
)
