package ro.jf.funds.commons.error

import kotlinx.serialization.Serializable

@Serializable
data class ErrorTO(
    val title: String,
    val detail: String?
) {
    companion object {
        fun internal(cause: Throwable) = ErrorTO(
            title = "Internal error",
            detail = cause.message
        )
    }
}
