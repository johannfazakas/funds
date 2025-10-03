package ro.jf.funds.fund.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.fund.api.serializer.AccountNameSerializer

@JvmInline
@Serializable(with = AccountNameSerializer::class)
value class AccountName(val value: String) {
    init {
        require(value.isNotBlank()) { "Account name must not be blank." }
    }

    override fun toString(): String = value
}
