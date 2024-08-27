package ro.jf.bk.fund.api.model

import kotlinx.serialization.Serializable
import ro.jf.bk.commons.serialization.UUIDSerializer
import java.util.*

@Serializable
data class FundTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val accounts: List<FundAccountTO>
)

@Serializable
data class FundAccountTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID
)
