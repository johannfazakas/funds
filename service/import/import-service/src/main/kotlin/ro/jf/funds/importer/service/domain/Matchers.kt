package ro.jf.funds.importer.service.domain

import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.importer.api.model.AccountMatcherTO
import ro.jf.funds.importer.api.model.ExchangeMatcherTO
import ro.jf.funds.importer.api.model.FundMatcherTO
import ro.jf.funds.importer.service.domain.exception.ImportDataException

fun List<AccountMatcherTO>.getAccountName(importAccountName: String): AccountName =
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

fun List<ExchangeMatcherTO>.getExchangeMatcher(importLabels: List<String>): ExchangeMatcherTO? =
    firstOrNull { matcher -> importLabels.any { importLabel -> matcher.matches(importLabel) } }

fun ExchangeMatcherTO.matches(importLabel: String): Boolean {
    return when (this) {
        is ExchangeMatcherTO.ByLabel -> this.label == importLabel
    }
}
