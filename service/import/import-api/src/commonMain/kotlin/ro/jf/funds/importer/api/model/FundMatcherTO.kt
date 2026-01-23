package ro.jf.funds.importer.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ro.jf.funds.fund.api.model.FundName

@Serializable
sealed class FundMatcherTO {
    abstract val fundName: FundName

    @Serializable
    @SerialName("by_account")
    data class ByAccount(
        val importAccountNames: List<String>,
        override val fundName: FundName,
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_label")
    data class ByLabel(
        val importLabels: List<String>,
        override val fundName: FundName,
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_account_label")
    data class ByAccountLabel(
        val importAccountNames: List<String>,
        val importLabels: List<String>,
        override val fundName: FundName,
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_label_with_post_transfer")
    data class ByLabelWithPostTransfer(
        val importLabels: List<String>,
        val initialFundName: FundName,
        override val fundName: FundName,
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_account_label_with_post_transfer")
    data class ByAccountLabelWithPostTransfer(
        val importAccountNames: List<String>,
        val importLabels: List<String>,
        val initialFundName: FundName,
        override val fundName: FundName,
    ) : FundMatcherTO()

    @Serializable
    @SerialName("by_account_label_with_pre_transfer")
    data class ByAccountLabelWithPreTransfer(
        val importAccountNames: List<String>,
        val importLabels: List<String>,
        val initialFundName: FundName,
        override val fundName: FundName,
    ) : FundMatcherTO()
}
