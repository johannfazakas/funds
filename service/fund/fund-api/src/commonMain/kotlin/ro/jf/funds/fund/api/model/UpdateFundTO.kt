package ro.jf.funds.fund.api.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateFundTO(
    val name: FundName? = null,
)
