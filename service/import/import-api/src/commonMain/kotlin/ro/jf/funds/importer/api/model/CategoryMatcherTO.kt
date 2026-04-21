package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.model.Category

@Serializable
data class CategoryMatcherTO(
    val importLabels: List<String>,
    val category: Category,
)
