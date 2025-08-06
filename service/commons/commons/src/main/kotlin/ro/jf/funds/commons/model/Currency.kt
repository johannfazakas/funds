package ro.jf.funds.commons.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.serialization.CurrencySerializer
import ro.jf.funds.commons.serialization.FinancialUnitSerializer
import ro.jf.funds.commons.serialization.SymbolSerializer

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

@Serializable(with = FinancialUnitSerializer::class)
sealed class FinancialUnit {
    abstract val value: String
    abstract val type: UnitType

    companion object {
        fun of(unitType: String, unit: String): FinancialUnit = when (UnitType.fromString(unitType)) {
            UnitType.CURRENCY -> Currency(unit)
            UnitType.SYMBOL -> Symbol(unit)
        }
    }
}

@Serializable(with = CurrencySerializer::class)
data class Currency(override val value: String) : FinancialUnit() {
    override val type = UnitType.CURRENCY

    companion object {
        val RON = Currency("RON")
        val EUR = Currency("EUR")
        val USD = Currency("USD")
    }
}

@Serializable(with = SymbolSerializer::class)
data class Symbol(override val value: String) : FinancialUnit() {
    override val type = UnitType.SYMBOL
}

