package ro.jf.funds.fund.api.model

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
data class LabelTO(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    val name: String,
)
