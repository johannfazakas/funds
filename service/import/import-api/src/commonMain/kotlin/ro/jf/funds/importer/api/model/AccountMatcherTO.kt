package ro.jf.funds.importer.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ro.jf.funds.fund.api.model.AccountName

@Serializable
sealed class AccountMatcherTO(
) {
    abstract val importAccountNames: List<String>
    abstract val accountName: AccountName?

    @Serializable
    @SerialName("by_name")
    data class ByName(
        override val importAccountNames: List<String>,
        override val accountName: AccountName,
    ) : AccountMatcherTO()

    @Serializable
    @SerialName("skipped")
    data class Skipped(
        override val importAccountNames: List<String>,
    ) : AccountMatcherTO() {
        override val accountName = null
    }
}
