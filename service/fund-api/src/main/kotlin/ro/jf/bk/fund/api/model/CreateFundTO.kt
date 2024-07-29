package ro.jf.bk.fund.api.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateFundTO(
    val name: String
)
