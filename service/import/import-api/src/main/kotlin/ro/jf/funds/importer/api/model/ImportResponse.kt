package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable

@Serializable
data class ImportResponse(
    // TODO(Johann) what else could be added here?
    val response: String
)
