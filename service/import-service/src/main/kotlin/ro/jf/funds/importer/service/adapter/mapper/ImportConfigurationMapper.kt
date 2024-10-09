package ro.jf.funds.importer.service.adapter.mapper

import ro.jf.funds.importer.api.model.AccountMatcherTO
import ro.jf.funds.importer.api.model.FundMatcherTO
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.importer.service.domain.model.AccountMatcher
import ro.jf.funds.importer.service.domain.model.FundMatcher
import ro.jf.funds.importer.service.domain.model.ImportConfiguration
import ro.jf.funds.importer.service.domain.model.ImportType

fun ImportConfigurationTO.toModel() = ImportConfiguration(
    importType = when (fileType) {
        ImportFileTypeTO.WALLET_CSV -> ImportType.WALLET_CSV
    },
    accountMatchers = accountMatchers.map { it.toModel() },
    fundMatchers = fundMatchers.map { it.toModel() }
)

fun AccountMatcherTO.toModel() = AccountMatcher(
    importAccountName = importAccountName,
    accountName = accountName
)

fun FundMatcherTO.toModel() = when (this) {
    is FundMatcherTO.ByLabel -> FundMatcher.ByLabel(
        importLabel = importLabel,
        fundName = fundName
    )

    is FundMatcherTO.ByLabelAndAccount -> FundMatcher.ByLabelAndAccount(
        importLabel = importLabel,
        importAccountName = importAccountName,
        fundName = fundName
    )

    is FundMatcherTO.ByLabelAndAccountWithImplicitTransfer -> FundMatcher.ByLabelAndAccountWithImplicitTransfer(
        importAccountName = importAccountName,
        importLabel = importLabel,
        initialFundName = initialFundName,
        destinationFundName = destinationFundName
    )
}
