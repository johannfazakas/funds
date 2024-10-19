package ro.jf.funds.importer.service.domain

data class ImportConfiguration(
    val importType: ImportType,
    val accountMatchers: AccountMatchers,
    val fundMatchers: FundMatchers,
)
