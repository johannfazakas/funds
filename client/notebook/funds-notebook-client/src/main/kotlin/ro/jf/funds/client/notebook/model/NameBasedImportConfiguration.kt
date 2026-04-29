package ro.jf.funds.client.notebook.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NameBasedImportConfiguration(
    val name: String,
    val accountMatchers: List<NameBasedAccountMatcher> = emptyList(),
    val categoryMatchers: List<NameBasedCategoryMatcher> = emptyList(),
    val exchangeMatchers: List<NameBasedExchangeMatcher> = emptyList(),
    val fundMatchers: List<NameBasedFundMatcher> = emptyList(),
)

@Serializable
data class NameBasedAccountMatcher(
    val importAccountNames: List<String>,
    val accountName: String? = null,
    val skipped: Boolean = false,
)

@Serializable
data class NameBasedCategoryMatcher(
    val importLabels: List<String>,
    val categoryName: String,
)

@Serializable
sealed class NameBasedExchangeMatcher {
    @Serializable
    @SerialName("by_label")
    data class ByLabel(val label: String) : NameBasedExchangeMatcher()
}

@Serializable
data class NameBasedFundMatcher(
    val accountNames: List<String>,
    val defaultFundName: String? = null,
    val byCategory: Map<String, NameBasedCategoryRule> = emptyMap(),
) {
    @Serializable
    data class NameBasedCategoryRule(
        val fundName: String,
        val intermediaryFundName: String? = null,
    )
}
