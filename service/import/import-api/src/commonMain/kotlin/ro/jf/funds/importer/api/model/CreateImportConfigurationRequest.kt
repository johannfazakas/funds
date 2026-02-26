package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateImportConfigurationRequest(
    val name: String,
    val accountMatchers: List<AccountMatcherTO> = emptyList(),
    val fundMatchers: List<FundMatcherTO> = emptyList(),
    val exchangeMatchers: List<ExchangeMatcherTO> = emptyList(),
    val labelMatchers: List<LabelMatcherTO> = emptyList(),
)
