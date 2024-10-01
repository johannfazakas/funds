package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable

@Serializable
data class KeysTO(
    val accountName: String,
    val currency: String,
    val amount: String,
    val date: String
)