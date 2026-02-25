package ro.jf.funds.importer.api.model

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
data class ImportFileTO(
    @Serializable(with = UuidSerializer::class)
    val importFileId: Uuid,
    val fileName: String,
    val type: ImportFileTypeTO,
    val status: ImportFileStatusTO,
    val importConfiguration: ImportFileConfigurationTO? = null,
    val createdAt: String,
)

@Serializable
data class ImportFileConfigurationTO(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    val name: String,
)
