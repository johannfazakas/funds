package ro.jf.funds.importer.service.domain

import ro.jf.funds.importer.api.model.AccountMatcherTO
import ro.jf.funds.importer.api.model.FundMatcherTO

fun List<AccountMatcherTO>.getAccountName(importAccountName: String): String =
    firstOrNull { it.importAccountName == importAccountName }
        ?.accountName
        ?: throw ImportDataException("Account name not matched: $importAccountName")

fun List<FundMatcherTO>.getFundMatcher(importAccountName: String, importLabel: String): FundMatcherTO =
    firstOrNull { it.matches(importAccountName, importLabel) }
        ?: throw ImportDataException("No fund matcher found for import account name: $importAccountName, import label: $importLabel.")

fun FundMatcherTO.matches(importAccountName: String, importLabel: String): Boolean {
    return when (this) {
        is FundMatcherTO.ByAccount -> this.importAccountName == importAccountName
        is FundMatcherTO.ByLabel -> this.importLabel == importLabel
        is FundMatcherTO.ByAccountLabel -> this.importAccountName == importAccountName && this.importLabel == importLabel
        is FundMatcherTO.ByAccountLabelWithTransfer -> this.importAccountName == importAccountName && this.importLabel == importLabel
    }
}
