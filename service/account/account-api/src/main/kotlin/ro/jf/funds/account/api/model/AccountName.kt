package ro.jf.funds.account.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.account.api.serializer.AccountNameSerializer

@JvmInline
@Serializable(with = AccountNameSerializer::class)
value class AccountName(val value: String) {
    init {
        require(value.isNotBlank()) { "Account name must not be blank." }
    }

    override fun toString(): String = value
}
