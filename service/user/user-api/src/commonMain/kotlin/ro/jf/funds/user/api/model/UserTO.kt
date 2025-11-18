package ro.jf.funds.user.api.model

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Serializable
import ro.jf.funds.user.api.serialization.KmpUuidSerializer

@Serializable
data class UserTO(
    @Serializable(with = KmpUuidSerializer::class)
    val id: Uuid,
    val username: String
)
