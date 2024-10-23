package ro.jf.funds.commons.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class FinancialUnit {
    abstract val value: String
}

@Serializable
@SerialName("currency")
data class Currency(override val value: String) : FinancialUnit() {
    companion object {
        val RON = Currency("RON")
        val EUR = Currency("EUR")
        val USD = Currency("USD")
    }
}

@Serializable
@SerialName("symbol")
data class Symbol(override val value: String) : FinancialUnit()
