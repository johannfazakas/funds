package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable

@Serializable
data class AccountMatcherTO(
    val importLabel: String,
    val accountName: String
)
