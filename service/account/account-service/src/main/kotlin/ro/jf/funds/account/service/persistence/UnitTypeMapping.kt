package ro.jf.funds.account.service.persistence

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Symbol

// TODO(Johann) might not be needed anymore, added this to class
internal object UnitTypeMapping {
    const val CURRENCY = "currency"
    const val SYMBOL = "symbol"
}

fun FinancialUnit.toUnitType(): String = when (this) {
    is Currency -> UnitTypeMapping.CURRENCY
    is Symbol -> UnitTypeMapping.SYMBOL
}

fun toFinancialUnit(unitType: String, unit: String): FinancialUnit = when (unitType) {
    UnitTypeMapping.CURRENCY -> Currency(unit)
    UnitTypeMapping.SYMBOL -> Symbol(unit)
    else -> throw IllegalArgumentException("Unknown unit type: $unitType")
}
