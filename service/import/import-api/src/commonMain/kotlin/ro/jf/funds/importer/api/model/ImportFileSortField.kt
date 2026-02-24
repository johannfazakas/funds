package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.model.SortField

@Serializable
enum class ImportFileSortField(override val value: String) : SortField {
    FILE_NAME("file_name"),
    CREATED_AT("created_at")
}
