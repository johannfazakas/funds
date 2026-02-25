package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.model.SortField

@Serializable
enum class ImportConfigurationSortField(override val value: String) : SortField {
    NAME("name"),
    CREATED_AT("created_at")
}
