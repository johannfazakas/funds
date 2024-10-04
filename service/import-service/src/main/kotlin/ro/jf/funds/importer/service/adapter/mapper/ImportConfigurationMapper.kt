package ro.jf.funds.importer.service.adapter.mapper

import ro.jf.funds.importer.api.model.AccountMatcherTO
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.importer.service.domain.model.AccountMatcher
import ro.jf.funds.importer.service.domain.model.ImportConfiguration
import ro.jf.funds.importer.service.domain.model.ImportType

fun ImportConfigurationTO.toModel() = ImportConfiguration(
    importType = when (fileType) {
        ImportFileTypeTO.WALLET_CSV -> ImportType.WALLET_CSV
    },
    accountMatchers = accountMatchers.map { it.toModel() }
)

fun AccountMatcherTO.toModel() = AccountMatcher(
    importLabel = importLabel,
    accountName = accountName
)
