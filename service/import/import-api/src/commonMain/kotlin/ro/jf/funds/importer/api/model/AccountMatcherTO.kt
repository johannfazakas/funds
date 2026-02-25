package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.fund.api.model.AccountName

@Serializable
data class AccountMatcherTO(
    val importAccountName: String,
    val accountName: AccountName? = null,
    val skipped: Boolean = false,
)
