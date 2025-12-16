package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.model.Label

@Serializable
data class LabelMatcherTO(
    val importLabel: String,
    val label: Label,
)
