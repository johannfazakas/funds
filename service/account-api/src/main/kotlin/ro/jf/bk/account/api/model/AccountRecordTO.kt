package ro.jf.bk.account.api.model

import kotlinx.serialization.Serializable
import ro.jf.bk.commons.serialization.BigDecimalSerializer
import ro.jf.bk.commons.serialization.UUIDSerializer
import java.math.BigDecimal
import java.util.*

@Serializable
data class AccountRecordTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val accountId: UUID,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val metadata: Map<String, String>
)
