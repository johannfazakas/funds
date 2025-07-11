package ro.jf.funds.importer.service.domain

import java.util.*

data class StartImportTaskCommand(
    val userId: UUID,
    val partNames: List<String>,
) {
    init {
        require(partNames.isNotEmpty()) { "At least one part name must be provided." }
        require(partNames.all { it.isNotBlank() }) { "Part names must not be blank." }
        require(partNames.distinct().size == partNames.size) { "Part names must be unique." }
    }
}
