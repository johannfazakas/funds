package ro.jf.funds.importer.service.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ro.jf.funds.fund.api.model.AccountName
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.platform.api.model.Label

@Serializable
data class ImportMatchers(
    val accountMatchers: List<AccountMatcher> = emptyList(),
    val fundMatchers: List<FundMatcher> = emptyList(),
    val exchangeMatchers: List<ExchangeMatcher> = emptyList(),
    val labelMatchers: List<LabelMatcher> = emptyList(),
) {
    fun getAccountMatcher(importAccountName: String): AccountMatcher =
        accountMatchers.firstOrNull { importAccountName == it.importAccountName }
            ?: throw ImportDataException("Account name not matched: $importAccountName")

    fun getFundMatcher(importAccountName: String, importLabels: List<String>): FundMatcher =
        fundMatchers.firstOrNull { it.matches(importAccountName, importLabels) }
            ?: throw ImportDataException("No fund matcher found for import account name: $importAccountName, import labels: $importLabels.")

    fun getLabelMatchers(importLabels: List<String>): List<LabelMatcher> =
        if (importLabels.isEmpty()) {
            emptyList()
        } else {
            labelMatchers.filter { matcher -> matcher.importLabels.any { it in importLabels } }
                .takeIf { it.isNotEmpty() }
                ?: throw ImportDataException("No label matcher found for import label: $importLabels.")
        }

    fun getExchangeMatcher(importLabels: List<String>): ExchangeMatcher? =
        exchangeMatchers.firstOrNull { matcher -> importLabels.any { importLabel -> matcher.matches(importLabel) } }
}

@Serializable
data class AccountMatcher(
    val importAccountName: String,
    val accountName: AccountName? = null,
    val skipped: Boolean = false,
)

@Serializable
data class FundMatcher(
    val fundName: FundName,
    val importAccountName: String? = null,
    val importLabel: String? = null,
    val intermediaryFundName: FundName? = null,
) {
    fun matches(importAccountName: String, importLabels: List<String>): Boolean {
        val accountMatch = this.importAccountName == null || this.importAccountName == importAccountName
        val labelMatch = this.importLabel == null || this.importLabel in importLabels
        return accountMatch && labelMatch
    }
}

@Serializable
sealed class ExchangeMatcher {
    abstract fun matches(importLabel: String): Boolean

    @Serializable
    @SerialName("by_label")
    data class ByLabel(
        val label: String,
    ) : ExchangeMatcher() {
        override fun matches(importLabel: String): Boolean = this.label == importLabel
    }
}

@Serializable
data class LabelMatcher(
    val importLabels: List<String>,
    val label: Label,
)
