package ro.jf.funds.importer.service.domain.model

import ro.jf.funds.importer.service.domain.exception.ImportDataException

data class FundMatchers(
    val matchers: List<FundMatcher>
) {
    constructor(vararg matchers: FundMatcher) : this(matchers.toList())

    fun getFundMatcher(importAccountName: String, importLabel: String): FundMatcher {
        val matchingMatchers = matchers.filter { it.matches(importAccountName, importLabel) }
        if (matchingMatchers.size != 1) {
            throw ImportDataException("Must match single fund matcher, but matched ${matchingMatchers.size}: $matchingMatchers.")
        }
        return matchingMatchers.first()
    }
}

sealed class FundMatcher {
    fun matches(importAccountName: String, importLabel: String): Boolean {
        return when (this) {
            is ByLabel -> this.importLabel == importLabel
            is ByAccountLabel -> this.importAccountName == importAccountName && this.importLabel == importLabel
            is ByAccountLabelWithTransfer -> this.importAccountName == importAccountName && this.importLabel == importLabel
        }
    }

    data class ByLabel(
        val importLabel: String,
        val fundName: String
    ) : FundMatcher()

    data class ByAccountLabel(
        val importAccountName: String,
        val importLabel: String,
        val fundName: String
    ) : FundMatcher()

    data class ByAccountLabelWithTransfer(
        val importAccountName: String,
        val importLabel: String,
        val initialFundName: String,
        val destinationFundName: String,
    ) : FundMatcher()
}
