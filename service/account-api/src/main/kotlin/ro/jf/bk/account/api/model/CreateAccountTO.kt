package ro.jf.bk.account.api.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateCurrencyAccountTO(
    val name: String,
    val currency: String,
)

@Serializable
data class CreateInstrumentAccountTO(
    val name: String,
    val currency: String,
    val symbol: String,
)
