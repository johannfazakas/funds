package ro.jf.funds.importer.service.domain.model

data class ImportConfiguration(
    val importType: ImportType,
    val accountMatchers: List<AccountMatcher>,
    val fundMatchers: List<FundMatcher>,
)

data class AccountMatcher(
    val importAccountName: String,
    val accountName: String
)

sealed class FundMatcher {
    data class ByLabel(
        val importLabel: String,
        val fundName: String
    ) : FundMatcher()

    data class ByLabelAndAccount(
        val importLabel: String,
        val importAccountName: String,
        val fundName: String
    ) : FundMatcher()

    data class ByLabelAndAccountWithImplicitTransfer(
        val importAccountName: String,
        val importLabel: String,
        val initialFundName: String,
        val destinationFundName: String,
    ) : FundMatcher()
}
