package ro.jf.funds.importer.service.domain.model

import ro.jf.funds.importer.service.domain.exception.ImportDataException

data class AccountMatchers(
    val matchers: List<AccountMatcher>
) {
    constructor(vararg matchers: AccountMatcher) : this(matchers.toList())

    private val accountNamesByImportAccountName = matchers.associate { it.importAccountName to it.accountName }

    fun getAccountName(importAccountName: String): String = accountNamesByImportAccountName[importAccountName]
        ?: throw ImportDataException("Account name not matched: $importAccountName")
}

data class AccountMatcher(
    val importAccountName: String,
    val accountName: String
)
