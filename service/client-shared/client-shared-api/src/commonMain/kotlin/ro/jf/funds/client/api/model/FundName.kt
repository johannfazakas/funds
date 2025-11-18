package ro.jf.funds.client.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.client.api.serialization.FundNameSerializer

@Serializable(with = FundNameSerializer::class)
data class FundName(val value: String) {
    init {
        require(value.isNotBlank()) { "Fund name must not be blank" }
    }

    override fun toString(): String = value
}
