package ro.jf.funds.importer.service.web.mapper

import ro.jf.funds.importer.api.model.AccountMatcherTO
import ro.jf.funds.importer.api.model.ExchangeMatcherTO
import ro.jf.funds.importer.api.model.FundMatcherTO
import ro.jf.funds.importer.api.model.CategoryMatcherTO
import ro.jf.funds.importer.service.domain.AccountMatcher
import ro.jf.funds.importer.service.domain.ExchangeMatcher
import ro.jf.funds.importer.service.domain.FundMatcher
import ro.jf.funds.importer.service.domain.ImportMatchers
import ro.jf.funds.importer.service.domain.CategoryMatcher

fun ImportMatchers.toMatcherTOs() = MatcherTOs(
    accountMatchers = accountMatchers.map { it.toTO() },
    fundMatchers = fundMatchers.map { it.toTO() },
    exchangeMatchers = exchangeMatchers.map { it.toTO() },
    categoryMatchers = categoryMatchers.map { it.toTO() },
)

data class MatcherTOs(
    val accountMatchers: List<AccountMatcherTO>,
    val fundMatchers: List<FundMatcherTO>,
    val exchangeMatchers: List<ExchangeMatcherTO>,
    val categoryMatchers: List<CategoryMatcherTO>,
)

fun AccountMatcherTO.toDomain() = AccountMatcher(
    importAccountNames = importAccountNames,
    accountId = accountId,
    skipped = skipped,
)

fun AccountMatcher.toTO() = AccountMatcherTO(
    importAccountNames = importAccountNames,
    accountId = accountId,
    skipped = skipped,
)

fun FundMatcherTO.toDomain() = FundMatcher(
    accountIds = accountIds,
    defaultFundId = defaultFundId,
    categoryRules = categoryRules.map { it.toDomain() },
)

fun FundMatcherTO.CategoryRule.toDomain() = FundMatcher.CategoryRule(
    categoryId = categoryId,
    fundId = fundId,
    intermediaryFundId = intermediaryFundId,
)

fun FundMatcher.toTO() = FundMatcherTO(
    accountIds = accountIds,
    defaultFundId = defaultFundId,
    categoryRules = categoryRules.map { it.toTO() },
)

fun FundMatcher.CategoryRule.toTO() = FundMatcherTO.CategoryRule(
    categoryId = categoryId,
    fundId = fundId,
    intermediaryFundId = intermediaryFundId,
)

fun ExchangeMatcherTO.toDomain(): ExchangeMatcher = when (this) {
    is ExchangeMatcherTO.ByLabel -> ExchangeMatcher.ByLabel(label = label)
}

fun ExchangeMatcher.toTO(): ExchangeMatcherTO = when (this) {
    is ExchangeMatcher.ByLabel -> ExchangeMatcherTO.ByLabel(label = label)
}

fun CategoryMatcherTO.toDomain() = CategoryMatcher(
    importLabels = importLabels,
    categoryId = categoryId,
)

fun CategoryMatcher.toTO() = CategoryMatcherTO(
    importLabels = importLabels,
    categoryId = categoryId,
)

fun toImportMatchers(
    accountMatchers: List<AccountMatcherTO>,
    fundMatchers: List<FundMatcherTO>,
    exchangeMatchers: List<ExchangeMatcherTO>,
    categoryMatchers: List<CategoryMatcherTO>,
) = ImportMatchers(
    accountMatchers = accountMatchers.map { it.toDomain() },
    fundMatchers = fundMatchers.map { it.toDomain() },
    exchangeMatchers = exchangeMatchers.map { it.toDomain() },
    categoryMatchers = categoryMatchers.map { it.toDomain() },
)
