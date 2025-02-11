package ro.jf.funds.commons.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.error.ErrorTO

@Serializable
sealed class GenericResponse {
    @Serializable
    @SerialName("success")
    data object Success : GenericResponse()

    @Serializable
    @SerialName("error")
    data class Error(val reason: ErrorTO) : GenericResponse()
}
