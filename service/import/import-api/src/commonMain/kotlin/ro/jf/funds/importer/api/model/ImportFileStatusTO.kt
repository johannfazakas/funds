package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable

@Serializable
enum class ImportFileStatusTO {
    PENDING,
    UPLOADED,
}
