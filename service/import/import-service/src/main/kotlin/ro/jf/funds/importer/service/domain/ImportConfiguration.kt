package ro.jf.funds.importer.service.domain

import java.time.LocalDateTime
import java.util.*

data class ImportConfiguration(
    val importConfigurationId: UUID,
    val userId: UUID,
    val name: String,
    val matchers: ImportMatchers,
    val createdAt: LocalDateTime,
)

data class CreateImportConfigurationCommand(
    val userId: UUID,
    val name: String,
    val matchers: ImportMatchers,
)

data class UpdateImportConfigurationCommand(
    val name: String?,
    val matchers: ImportMatchers?,
)
