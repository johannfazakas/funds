package ro.jf.funds.importer.api.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class FundMatcherTO {
    @Serializable
    @SerialName("by_label")
    data class ByLabel(
        val importLabel: String,
        val fundName: String
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_label_account")
    data class ByLabelAndAccount(
        val importLabel: String,
        val importAccountName: String,
        val fundName: String
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_label_account_with_implicit_transfer")
    data class ByLabelAndAccountWithImplicitTransfer(
        val importAccountName: String,
        val importLabel: String,
        val initialFundName: String,
        val destinationFundName: String,
    ) : FundMatcherTO()
}
