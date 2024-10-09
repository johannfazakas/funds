package ro.jf.funds.importer.service.domain.model

data class FundMatchers(
    val matchers: List<FundMatcher>
) {
    constructor(vararg matchers: FundMatcher) : this(matchers.toList())
}

sealed class FundMatcher {
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
