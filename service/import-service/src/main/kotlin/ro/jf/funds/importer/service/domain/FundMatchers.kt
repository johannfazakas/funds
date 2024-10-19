package ro.jf.funds.importer.service.domain

data class FundMatchers(
    val matchers: List<FundMatcher>
) {
    constructor(vararg matchers: FundMatcher) : this(matchers.toList())

    fun getFundMatcher(importAccountName: String, importLabel: String): FundMatcher {
        return matchers.firstOrNull { it.matches(importAccountName, importLabel) }
            ?: throw ImportDataException("No fund matcher found for import account name: $importAccountName, import label: $importLabel.")
    }
}

sealed class FundMatcher {
    abstract val fundName: String

    fun matches(importAccountName: String, importLabel: String): Boolean {
        return when (this) {
            is ByAccount -> this.importAccountName == importAccountName
            is ByLabel -> this.importLabel == importLabel
            is ByAccountLabel -> this.importAccountName == importAccountName && this.importLabel == importLabel
            is ByAccountLabelWithTransfer -> this.importAccountName == importAccountName && this.importLabel == importLabel
        }
    }

    data class ByAccount(
        val importAccountName: String,
        override val fundName: String
    ) : FundMatcher()

    data class ByLabel(
        val importLabel: String,
        override val fundName: String
    ) : FundMatcher()

    data class ByAccountLabel(
        val importAccountName: String,
        val importLabel: String,
        override val fundName: String
    ) : FundMatcher()

    data class ByAccountLabelWithTransfer(
        val importAccountName: String,
        val importLabel: String,
        val initialFundName: String,
        override val fundName: String,
    ) : FundMatcher()
}
