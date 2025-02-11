package ro.jf.funds.commons.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

enum class UnitType(val value: String) {
    CURRENCY("currency"),
    SYMBOL("symbol"),
    ;

    companion object {
        fun fromString(value: String): UnitType = entries.firstOrNull { it.value == value }
            ?: throw IllegalArgumentException("Unknown unit type: $value")
    }
}

fun toFinancialUnit(unitType: String, unit: String): FinancialUnit =
    toFinancialUnit(UnitType.fromString(unitType), unit)

fun toFinancialUnit(unitType: UnitType, unit: String): FinancialUnit = when (unitType) {
    UnitType.CURRENCY -> Currency(unit)
    UnitType.SYMBOL -> Symbol(unit)
}

@Serializable
sealed class FinancialUnit {
    abstract val value: String
    abstract val unitType: UnitType
}

@Serializable
@SerialName("currency")
data class Currency(override val value: String) : FinancialUnit() {
    @Transient
    override val unitType = UnitType.CURRENCY

    companion object {
        val RON = Currency("RON")
        val EUR = Currency("EUR")
        val USD = Currency("USD")
    }
}

@Serializable
@SerialName("symbol")
data class Symbol(override val value: String) : FinancialUnit() {
    @Transient
    override val unitType = UnitType.SYMBOL
}

