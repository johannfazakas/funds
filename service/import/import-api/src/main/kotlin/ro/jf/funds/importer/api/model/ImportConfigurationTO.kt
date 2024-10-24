package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable

@Serializable
data class ImportConfigurationTO(
    val fileType: ImportFileTypeTO,
    val accountMatchers: List<AccountMatcherTO>,
    val fundMatchers: List<FundMatcherTO>,
)