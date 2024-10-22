package ro.jf.funds.user.api.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserTO(
    val username: String
)
