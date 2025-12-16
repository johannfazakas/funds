package ro.jf.funds.user.api.model

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
data class UserTO(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    val username: String
)
