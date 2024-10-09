package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable

@Serializable
data class AccountMatcherTO(
    val importAccountName: String,
    val accountName: String
) {
    constructor(pair: Pair<String, String>) : this(pair.first, pair.second)
}
