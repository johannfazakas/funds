package ro.jf.funds.importer.service.web.mapper

import ro.jf.funds.importer.api.model.AccountMatcherTO
import ro.jf.funds.importer.api.model.FundMatcherTO
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.importer.service.domain.*

fun ImportConfigurationTO.toModel() = ImportConfiguration(
    importType = when (fileType) {
        ImportFileTypeTO.WALLET_CSV -> ImportType.WALLET_CSV
    },
    accountMatchers = AccountMatchers(accountMatchers.map { it.toModel() }),
    fundMatchers = FundMatchers(fundMatchers.map { it.toModel() })
)

fun AccountMatcherTO.toModel() = AccountMatcher(
    importAccountName = importAccountName,
    accountName = accountName
)

fun FundMatcherTO.toModel() = when (this) {

    is FundMatcherTO.ByAccount -> FundMatcher.ByAccount(
        importAccountName = importAccountName,
        fundName = fundName
    )

    is FundMatcherTO.ByLabel -> FundMatcher.ByLabel(
        importLabel = importLabel,
        fundName = fundName
    )

    is FundMatcherTO.ByAccountLabel -> FundMatcher.ByAccountLabel(
        importAccountName = importAccountName,
        importLabel = importLabel,
        fundName = fundName
    )

    is FundMatcherTO.ByAccountLabelWithTransfer -> FundMatcher.ByAccountLabelWithTransfer(
        importAccountName = importAccountName,
        importLabel = importLabel,
        initialFundName = initialFundName,
        fundName = fundName
    )
}
