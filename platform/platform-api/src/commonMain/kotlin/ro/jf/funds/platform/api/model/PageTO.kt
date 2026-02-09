package ro.jf.funds.platform.api.model

import kotlinx.serialization.Serializable

@Serializable
data class PageTO<T>(
    val items: List<T>,
    val total: Long,
)
