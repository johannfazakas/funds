package ro.jf.bk.user.api.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserTO(
    val username: String
)
