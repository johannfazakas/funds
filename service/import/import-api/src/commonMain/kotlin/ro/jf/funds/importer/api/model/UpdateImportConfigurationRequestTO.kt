package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateImportConfigurationRequestTO(
    val name: String? = null,
    val accountMatchers: List<AccountMatcherTO>? = null,
    val fundMatchers: List<FundMatcherTO>? = null,
    val exchangeMatchers: List<ExchangeMatcherTO>? = null,
    val labelMatchers: List<LabelMatcherTO>? = null,
)
