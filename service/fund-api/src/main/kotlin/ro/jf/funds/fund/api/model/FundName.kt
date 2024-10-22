package ro.jf.funds.fund.api.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class FundName(val value: String) {
    init {
        require(value.isNotBlank()) { "Fund name must not be blank" }
    }

    override fun toString(): String = value
}