package ro.jf.bk.account.api.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateCurrencyAccountTO(
    val name: AccountName,
    val currency: String,
)

@Serializable
data class CreateInstrumentAccountTO(
    val name: AccountName,
    val currency: String,
    val symbol: String,
)
