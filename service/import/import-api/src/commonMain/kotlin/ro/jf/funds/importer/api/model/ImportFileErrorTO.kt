package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable

@Serializable
data class ImportFileErrorTO(
    val title: String,
    val detail: String?,
)
