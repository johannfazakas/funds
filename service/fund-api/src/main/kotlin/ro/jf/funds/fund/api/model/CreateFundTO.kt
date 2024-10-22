package ro.jf.funds.fund.api.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateFundTO(
    val name: FundName,
)
