package ro.jf.funds.importer.service.domain.model

data class ImportConfiguration(
    val importType: ImportType,
    val accountMatchers: List<AccountMatcher>
)

data class AccountMatcher(
    val importLabel: String,
    val accountName: String
)
