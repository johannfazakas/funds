package ro.jf.bk.account.api.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class AccountName(val value: String) {
    init {
        require(value.isNotBlank()) { "Account name must not be blank." }
    }

    override fun toString(): String = value
}
