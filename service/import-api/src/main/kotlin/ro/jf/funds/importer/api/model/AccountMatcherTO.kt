package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable
import ro.jf.bk.account.api.model.AccountName

@Serializable
data class AccountMatcherTO(
    val importAccountName: String,
    val accountName: AccountName
) {
    constructor(pair: Pair<String, AccountName>) : this(pair.first, pair.second)
}
