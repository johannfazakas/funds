package ro.jf.funds.platform.jvm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.jvm.error.ErrorTO

@Serializable
sealed class GenericResponse {
    @Serializable
    @SerialName("success")
    data object Success : GenericResponse()

    @Serializable
    @SerialName("error")
    data class Error(val reason: ErrorTO) : GenericResponse()
}
