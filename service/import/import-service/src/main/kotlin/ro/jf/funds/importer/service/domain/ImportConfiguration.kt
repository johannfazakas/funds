package ro.jf.funds.importer.service.domain

import com.benasher44.uuid.Uuid
import java.time.LocalDateTime

data class ImportConfiguration(
    val importConfigurationId: Uuid,
    val userId: Uuid,
    val name: String,
    val matchers: ImportMatchers,
    val createdAt: LocalDateTime,
)

data class CreateImportConfigurationCommand(
    val userId: Uuid,
    val name: String,
    val matchers: ImportMatchers,
)

data class UpdateImportConfigurationCommand(
    val name: String?,
    val matchers: ImportMatchers?,
)
