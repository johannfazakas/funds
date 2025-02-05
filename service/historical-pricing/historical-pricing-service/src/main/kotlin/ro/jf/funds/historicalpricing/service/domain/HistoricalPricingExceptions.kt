package ro.jf.funds.historicalpricing.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.FinancialUnit

sealed class HistoricalPricingExceptions : RuntimeException() {
    class HistoricalPriceNotFound(
        val sourceUnit: FinancialUnit, val targetUnit: FinancialUnit, val date: LocalDate,
    ) : HistoricalPricingExceptions()

    class ConversionNotPermitted(
        val sourceUnit: FinancialUnit, val targetUnit: FinancialUnit,
    ) : HistoricalPricingExceptions()
}
