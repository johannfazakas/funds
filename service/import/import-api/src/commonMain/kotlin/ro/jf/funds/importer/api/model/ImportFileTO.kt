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
)
