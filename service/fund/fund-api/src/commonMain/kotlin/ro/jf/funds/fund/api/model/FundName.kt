package ro.jf.funds.fund.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.fund.api.serializer.FundNameSerializer

@kotlin.jvm.JvmInline
@Serializable(with = FundNameSerializer::class)
value class FundName(val value: String) {
    init {
        require(value.isNotBlank()) { "Fund name must not be blank" }
    }

    override fun toString(): String = value
}