package ro.jf.funds.importer.api.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class FundMatcherTO {
    abstract val fundName: String

    @Serializable
    @SerialName("by_account")
    data class ByAccount(
        val importAccountName: String,
        override val fundName: String
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_label")
    data class ByLabel(
        val importLabel: String,
        override val fundName: String
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_account_label")
    data class ByAccountLabel(
        val importAccountName: String,
        val importLabel: String,
        override val fundName: String
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_account_label_with_transfer")
    data class ByAccountLabelWithTransfer(
        val importAccountName: String,
        val importLabel: String,
        val initialFundName: String,
        override val fundName: String,
    ) : FundMatcherTO()
}
