package ro.jf.funds.importer.api.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class ExchangeMatcherTO {
    @Serializable
    @SerialName("by_label")
    data class ByLabel(
        val label: String,
    ) : ExchangeMatcherTO()
}
