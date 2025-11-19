package ro.jf.funds.fund.api.model

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.api.serialization.UuidSerializer

@Serializable
data class FundTO(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    val name: FundName,
)
