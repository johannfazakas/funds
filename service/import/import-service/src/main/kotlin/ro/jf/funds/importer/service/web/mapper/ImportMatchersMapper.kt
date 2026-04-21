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
    importAccountName = importAccountName,
    accountName = accountName,
    skipped = skipped,
)

fun AccountMatcher.toTO() = AccountMatcherTO(
    importAccountName = importAccountName,
    accountName = accountName,
    skipped = skipped,
)

fun FundMatcherTO.toDomain() = FundMatcher(
    fundName = fundName,
    importAccountName = importAccountName,
    importLabel = importLabel,
    intermediaryFundName = intermediaryFundName,
)

fun FundMatcher.toTO() = FundMatcherTO(
    fundName = fundName,
    importAccountName = importAccountName,
    importLabel = importLabel,
    intermediaryFundName = intermediaryFundName,
)

fun ExchangeMatcherTO.toDomain(): ExchangeMatcher = when (this) {
    is ExchangeMatcherTO.ByLabel -> ExchangeMatcher.ByLabel(label = label)
}

fun ExchangeMatcher.toTO(): ExchangeMatcherTO = when (this) {
    is ExchangeMatcher.ByLabel -> ExchangeMatcherTO.ByLabel(label = label)
}

fun CategoryMatcherTO.toDomain() = CategoryMatcher(
    importLabels = importLabels,
    category = category,
)

fun CategoryMatcher.toTO() = CategoryMatcherTO(
    importLabels = importLabels,
    category = category,
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

