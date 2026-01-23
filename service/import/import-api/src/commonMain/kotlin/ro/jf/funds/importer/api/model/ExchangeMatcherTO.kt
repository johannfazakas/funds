package ro.jf.funds.importer.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ExchangeMatcherTO {
    @Serializable
    @SerialName("by_label")
    data class ByLabel(
        val label: String,
    ) : ExchangeMatcherTO()
}
