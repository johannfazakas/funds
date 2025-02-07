package ro.jf.funds.reporting.api.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class ReportingFeatureTO {
    @Serializable
    @SerialName("min_max_total_value")
    data object MinMaxTotalValue : ReportingFeatureTO()
}
