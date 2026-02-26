package ro.jf.funds.importer.api.model

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
data class CreateImportFileRequest(
    val fileName: String,
    val type: ImportFileTypeTO,
    @Serializable(with = UuidSerializer::class)
    val importConfigurationId: Uuid,
)
