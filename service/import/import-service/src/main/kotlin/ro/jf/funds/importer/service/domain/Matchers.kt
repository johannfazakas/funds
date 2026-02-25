package ro.jf.funds.importer.service.domain

import ro.jf.funds.importer.api.model.AccountMatcherTO
import ro.jf.funds.importer.api.model.ExchangeMatcherTO
import ro.jf.funds.importer.api.model.FundMatcherTO
import ro.jf.funds.importer.api.model.LabelMatcherTO
import ro.jf.funds.importer.service.domain.exception.ImportDataException

fun List<AccountMatcherTO>.getAccountMatcher(importAccountName: String): AccountMatcherTO =
    firstOrNull { importAccountName == it.importAccountName }
        ?: throw ImportDataException("Account name not matched: $importAccountName")

fun List<FundMatcherTO>.getFundMatcher(importAccountName: String, importLabels: List<String>): FundMatcherTO =
    firstOrNull { it.matches(importAccountName, importLabels) }
        ?: throw ImportDataException("No fund matcher found for import account name: $importAccountName, import labels: $importLabels.")

fun FundMatcherTO.matches(importAccountName: String, importLabels: List<String>): Boolean {
    return when (this) {
        is FundMatcherTO.ByAccount -> importAccountName in this.importAccountNames
        is FundMatcherTO.ByLabel -> this.importLabels.any { it in importLabels }
        is FundMatcherTO.ByAccountLabel -> importAccountName in this.importAccountNames && this.importLabels.any { it in importLabels }
        is FundMatcherTO.ByLabelWithPostTransfer -> this.importLabels.any { it in importLabels }
        is FundMatcherTO.ByAccountLabelWithPostTransfer -> importAccountName in this.importAccountNames && this.importLabels.any { it in importLabels }
        is FundMatcherTO.ByAccountLabelWithPreTransfer -> importAccountName in this.importAccountNames && this.importLabels.any { it in importLabels }
    }
}

fun List<LabelMatcherTO>.getLabelMatchers(importLabels: List<String>): List<LabelMatcherTO> =
    if (importLabels.isEmpty()) {
        emptyList()
    } else {
        this.filter { matcher: LabelMatcherTO -> matcher.importLabels.any { it in importLabels } }
            .takeIf { it.isNotEmpty() }
            ?: throw ImportDataException("No label matcher found for import label: $importLabels.")
    }

fun List<ExchangeMatcherTO>.getExchangeMatcher(importLabels: List<String>): ExchangeMatcherTO? =
    firstOrNull { matcher -> importLabels.any { importLabel -> matcher.matches(importLabel) } }

fun ExchangeMatcherTO.matches(importLabel: String): Boolean {
    return when (this) {
        is ExchangeMatcherTO.ByLabel -> this.label == importLabel
    }
}
