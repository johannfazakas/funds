package ro.jf.funds.importer.api.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import ro.jf.funds.fund.api.model.FundName

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class FundMatcherTO {
    abstract val fundName: FundName

    @Serializable
    @SerialName("by_account")
    data class ByAccount(
        val importAccountName: String,
        override val fundName: FundName,
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_label")
    data class ByLabel(
        val importLabel: String,
        override val fundName: FundName,
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_account_label")
    data class ByAccountLabel(
        val importAccountName: String,
        val importLabel: String,
        override val fundName: FundName,
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_label_with_post_transfer")
    data class ByLabelWithPostTransfer(
        val importLabel: String,
        val initialFundName: FundName,
        override val fundName: FundName,
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_account_label_with_post_transfer")
    data class ByAccountLabelWithPostTransfer(
        val importAccountName: String,
        val importLabel: String,
        val initialFundName: FundName,
        override val fundName: FundName,
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_account_label_with_pre_transfer")
    data class ByAccountLabelWithPreTransfer(
        val importAccountName: String,
        val importLabel: String,
        val initialFundName: FundName,
        override val fundName: FundName,
    ) : FundMatcherTO()
}
