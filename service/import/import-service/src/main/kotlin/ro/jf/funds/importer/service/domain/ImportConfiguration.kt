package ro.jf.funds.importer.service.domain

import kotlinx.serialization.Serializable
import ro.jf.funds.importer.api.model.AccountMatcherTO
import ro.jf.funds.importer.api.model.ExchangeMatcherTO
import ro.jf.funds.importer.api.model.FundMatcherTO
import ro.jf.funds.importer.api.model.LabelMatcherTO
import java.time.LocalDateTime
import java.util.*

@Serializable
data class ImportConfigurationMatchersTO(
    val accountMatchers: List<AccountMatcherTO> = emptyList(),
    val fundMatchers: List<FundMatcherTO> = emptyList(),
    val exchangeMatchers: List<ExchangeMatcherTO> = emptyList(),
    val labelMatchers: List<LabelMatcherTO> = emptyList(),
)

data class ImportConfiguration(
    val importConfigurationId: UUID,
    val userId: UUID,
    val name: String,
    val matchers: ImportConfigurationMatchersTO,
    val createdAt: LocalDateTime,
)

data class CreateImportConfigurationCommand(
    val userId: UUID,
    val name: String,
    val matchers: ImportConfigurationMatchersTO,
)

data class UpdateImportConfigurationCommand(
    val name: String?,
    val matchers: ImportConfigurationMatchersTO?,
)
