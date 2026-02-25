package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.fund.api.model.FundName

@Serializable
data class FundMatcherTO(
    val fundName: FundName,
    val importAccountName: String? = null,
    val importLabel: String? = null,
    val intermediaryFundName: FundName? = null,
)
