package ro.jf.funds.importer.service.service

import ro.jf.funds.importer.api.model.ImportConfigurationSortField
import ro.jf.funds.importer.service.domain.AccountMatcher
import ro.jf.funds.importer.service.domain.CreateImportConfigurationCommand
import ro.jf.funds.importer.service.domain.FundMatcher
import ro.jf.funds.importer.service.domain.ImportConfiguration
import ro.jf.funds.importer.service.domain.ImportMatchers
import ro.jf.funds.importer.service.domain.UpdateImportConfigurationCommand
import ro.jf.funds.importer.service.domain.exception.ImportConfigurationValidationException
import ro.jf.funds.importer.service.persistence.ImportConfigurationRepository
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.SortRequest
import com.benasher44.uuid.Uuid
import ro.jf.funds.platform.jvm.persistence.PagedResult

class ImportConfigurationService(
    private val importConfigurationRepository: ImportConfigurationRepository,
) {
    suspend fun createImportConfiguration(
        userId: Uuid,
        name: String,
        matchers: ImportMatchers,
    ): ImportConfiguration {
        validateAccountMatchers(matchers.accountMatchers)
        validateFundMatchers(matchers.fundMatchers)
        return importConfigurationRepository.create(CreateImportConfigurationCommand(userId, name, matchers))
    }

    suspend fun getImportConfiguration(userId: Uuid, importConfigurationId: Uuid): ImportConfiguration? {
        return importConfigurationRepository.findById(userId, importConfigurationId)
    }

    suspend fun listImportConfigurations(
        userId: Uuid,
        pageRequest: PageRequest? = null,
        sortRequest: SortRequest<ImportConfigurationSortField>? = null,
    ): PagedResult<ImportConfiguration> {
        return importConfigurationRepository.list(userId, pageRequest, sortRequest)
    }

    suspend fun updateImportConfiguration(
        userId: Uuid,
        importConfigurationId: Uuid,
        command: UpdateImportConfigurationCommand,
    ): ImportConfiguration? {
        command.matchers?.let {
            validateAccountMatchers(it.accountMatchers)
            validateFundMatchers(it.fundMatchers)
        }
        return importConfigurationRepository.update(userId, importConfigurationId, command)
    }

    suspend fun deleteImportConfiguration(userId: Uuid, importConfigurationId: Uuid): Boolean {
        return importConfigurationRepository.delete(userId, importConfigurationId)
    }

    private fun validateFundMatchers(matchers: List<FundMatcher>) {
        matchers.forEach { matcher ->
            if (matcher.importAccountName == null && matcher.importLabel == null) {
                throw ImportConfigurationValidationException("Fund matcher for '${matcher.fundName}' must have at least importAccountName or importLabel.")
            }
        }
    }

    private fun validateAccountMatchers(matchers: List<AccountMatcher>) {
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
