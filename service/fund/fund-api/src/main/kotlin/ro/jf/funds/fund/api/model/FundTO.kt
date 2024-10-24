package ro.jf.funds.fund.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.util.*

@Serializable
data class FundTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: FundName,
)
