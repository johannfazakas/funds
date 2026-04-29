package ro.jf.funds.importer.service.domain

import com.benasher44.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ro.jf.funds.importer.service.domain.exception.ImportConfigurationValidationException
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
data class ImportMatchers(
    val accountMatchers: List<AccountMatcher> = emptyList(),
    val fundMatchers: List<FundMatcher> = emptyList(),
    val exchangeMatchers: List<ExchangeMatcher> = emptyList(),
    val categoryMatchers: List<CategoryMatcher> = emptyList(),
) {
    init {
        val duplicateImportNames = accountMatchers.flatMap { it.importAccountNames }
            .groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicateImportNames.isNotEmpty())
            throw ImportConfigurationValidationException("Duplicate import account names: $duplicateImportNames.")

        val duplicateAccountIds = fundMatchers.flatMap { it.accountIds }
            .groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicateAccountIds.isNotEmpty())
            throw ImportConfigurationValidationException("Account ID appears in multiple fund matchers: $duplicateAccountIds.")

        val duplicateLabels = categoryMatchers.flatMap { it.importLabels }
            .groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicateLabels.isNotEmpty())
            throw ImportConfigurationValidationException("Duplicate import labels: $duplicateLabels.")
    }

    fun getAccountMatcher(importAccountName: String): AccountMatcher =
        accountMatchers.firstOrNull { importAccountName in it.importAccountNames }
            ?: throw ImportDataException("Account name not matched: '$importAccountName'. Available account matchers: ${accountMatchers.map { it.importAccountNames }}")

    fun getCategoryMatcher(importLabels: List<String>): CategoryMatcher? =
        if (importLabels.isEmpty()) {
            null
        } else {
            categoryMatchers.firstOrNull { matcher -> matcher.importLabels.any { it in importLabels } }
                ?: throw ImportDataException("No category matcher found for import labels: $importLabels. Available category matchers: ${categoryMatchers.map { it.importLabels }}")
        }

    fun findCategoryMatcher(importLabels: List<String>): CategoryMatcher? {
        if (importLabels.isEmpty()) return null
        val match = categoryMatchers.firstOrNull { matcher -> matcher.importLabels.any { it in importLabels } }
        if (match != null) return match
        val nonExchangeLabels = importLabels.filter { label -> exchangeMatchers.none { it.matches(label) } }
        if (nonExchangeLabels.isEmpty()) return null
        throw ImportDataException("No category matcher found for import labels: $importLabels. Available category matchers: ${categoryMatchers.map { it.importLabels }}")
    }

    fun getFundMatcher(accountId: Uuid): FundMatcher =
        fundMatchers.firstOrNull { accountId in it.accountIds }
            ?: throw ImportDataException("No fund matcher found for account ID: $accountId. Available fund matchers account IDs: ${fundMatchers.map { it.accountIds }}")

    fun getExchangeMatcher(importLabels: List<String>): ExchangeMatcher? =
        exchangeMatchers.firstOrNull { matcher -> importLabels.any { importLabel -> matcher.matches(importLabel) } }
}

@Serializable
data class AccountMatcher(
    val importAccountNames: List<String>,
    @Serializable(with = UuidSerializer::class)
    val accountId: Uuid? = null,
    val skipped: Boolean = false,
) {
    init {
        if (importAccountNames.isEmpty())
            throw ImportConfigurationValidationException("Account matcher must have at least one import account name.")
        if (skipped && accountId != null)
            throw ImportConfigurationValidationException("Skipped account matcher must not have an accountId.")
        if (!skipped && accountId == null)
            throw ImportConfigurationValidationException("Non-skipped account matcher must have an accountId.")
    }
}

@Serializable
data class FundMatcher(
    val accountIds: List<@Serializable(with = UuidSerializer::class) Uuid>,
    @Serializable(with = UuidSerializer::class)
    val defaultFundId: Uuid? = null,
    val categoryRules: List<CategoryRule> = emptyList(),
) {
    init {
        if (accountIds.isEmpty())
            throw ImportConfigurationValidationException("Fund matcher must have at least one account ID.")
        val duplicateCategoryIds = categoryRules.groupBy { it.categoryId }.filter { it.value.size > 1 }.keys
        if (duplicateCategoryIds.isNotEmpty())
            throw ImportConfigurationValidationException("Duplicate category IDs in fund matcher: $duplicateCategoryIds.")
    }
    @Serializable
    data class CategoryRule(
        @Serializable(with = UuidSerializer::class)
        val categoryId: Uuid,
        @Serializable(with = UuidSerializer::class)
        val fundId: Uuid,
        @Serializable(with = UuidSerializer::class)
        val intermediaryFundId: Uuid? = null,
    )

    data class ResolvedFund(
        val fundId: Uuid,
        val intermediaryFundId: Uuid? = null,
    )

    fun resolve(categoryId: Uuid?): ResolvedFund {
        if (categoryId != null) {
            val categoryRule = categoryRules.firstOrNull { it.categoryId == categoryId }
            if (categoryRule != null) {
                return ResolvedFund(categoryRule.fundId, categoryRule.intermediaryFundId)
            }
        }
        return defaultFundId?.let { ResolvedFund(it) }
            ?: throw ImportDataException("No fund matcher category rule found for category ID: $categoryId and no default fund configured. Available category rules: ${categoryRules.map { it.categoryId }}")
    }
}

@Serializable
sealed class ExchangeMatcher {
    abstract fun matches(importLabel: String): Boolean

    @Serializable
    @SerialName("by_label")
    data class ByLabel(
        val label: String,
    ) : ExchangeMatcher() {
        override fun matches(importLabel: String): Boolean = this.label == importLabel
    }
}

@Serializable
data class CategoryMatcher(
    val importLabels: List<String>,
    @Serializable(with = UuidSerializer::class)
    val categoryId: Uuid,
) {
    init {
        if (importLabels.isEmpty())
            throw ImportConfigurationValidationException("Category matcher must have at least one import label.")
    }
}
