package ro.jf.funds.platform.api.model

import kotlinx.serialization.Serializable

interface SortField {
    val value: String
}

data class SortRequest<T : SortField>(
    val field: T,
    val order: SortOrder = SortOrder.ASC,
)

@Serializable
enum class SortOrder { ASC, DESC }
