package ro.jf.funds.user.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.util.*

@Serializable
data class UserTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val username: String
)
