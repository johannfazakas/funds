package ro.jf.funds.importer.service.service

import ro.jf.funds.importer.api.model.AccountMatcherTO
import ro.jf.funds.importer.api.model.ImportConfigurationSortField
import ro.jf.funds.importer.service.domain.CreateImportConfigurationCommand
import ro.jf.funds.importer.service.domain.ImportConfiguration
import ro.jf.funds.importer.service.domain.ImportConfigurationMatchersTO
import ro.jf.funds.importer.service.domain.UpdateImportConfigurationCommand
import ro.jf.funds.importer.service.domain.exception.ImportConfigurationValidationException
import ro.jf.funds.importer.service.persistence.ImportConfigurationRepository
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.platform.jvm.persistence.PagedResult
import java.util.*

class ImportConfigurationService(
    private val importConfigurationRepository: ImportConfigurationRepository,
) {
    suspend fun createImportConfiguration(
        userId: UUID,
        name: String,
        matchers: ImportConfigurationMatchersTO,
    ): ImportConfiguration {
        validateAccountMatchers(matchers.accountMatchers)
        return importConfigurationRepository.create(CreateImportConfigurationCommand(userId, name, matchers))
    }

    suspend fun getImportConfiguration(userId: UUID, importConfigurationId: UUID): ImportConfiguration? {
        return importConfigurationRepository.findById(userId, importConfigurationId)
    }

    suspend fun listImportConfigurations(
        userId: UUID,
        pageRequest: PageRequest? = null,
        sortRequest: SortRequest<ImportConfigurationSortField>? = null,
    ): PagedResult<ImportConfiguration> {
        return importConfigurationRepository.list(userId, pageRequest, sortRequest)
    }

    suspend fun updateImportConfiguration(
        userId: UUID,
        importConfigurationId: UUID,
        command: UpdateImportConfigurationCommand,
    ): ImportConfiguration? {
        command.matchers?.let { validateAccountMatchers(it.accountMatchers) }
        return importConfigurationRepository.update(userId, importConfigurationId, command)
    }

    suspend fun deleteImportConfiguration(userId: UUID, importConfigurationId: UUID): Boolean {
        return importConfigurationRepository.delete(userId, importConfigurationId)
    }

    private fun validateAccountMatchers(matchers: List<AccountMatcherTO>) {
        matchers.forEach { matcher ->
            if (matcher.skipped && matcher.accountName != null) {
                throw ImportConfigurationValidationException("Skipped account matcher '${matcher.importAccountName}' must not have an accountName.")
            }
            if (!matcher.skipped && matcher.accountName == null) {
                throw ImportConfigurationValidationException("Non-skipped account matcher '${matcher.importAccountName}' must have an accountName.")
            }
        }
    }
}
