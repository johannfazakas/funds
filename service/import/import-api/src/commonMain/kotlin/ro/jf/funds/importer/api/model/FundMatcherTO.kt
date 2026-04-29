package ro.jf.funds.importer.api.model

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
data class FundMatcherTO(
    val accountIds: List<@Serializable(with = UuidSerializer::class) Uuid>,
    @Serializable(with = UuidSerializer::class)
    val defaultFundId: Uuid? = null,
    val categoryRules: List<CategoryRule> = emptyList(),
) {
    @Serializable
    data class CategoryRule(
        @Serializable(with = UuidSerializer::class)
        val categoryId: Uuid,
        @Serializable(with = UuidSerializer::class)
        val fundId: Uuid,
        @Serializable(with = UuidSerializer::class)
        val intermediaryFundId: Uuid? = null,
    )
}
