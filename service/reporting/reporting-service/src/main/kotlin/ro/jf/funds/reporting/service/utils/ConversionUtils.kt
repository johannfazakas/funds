package ro.jf.funds.reporting.service.utils

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import java.math.BigDecimal

fun getConversionRate(
    response: ConversionsResponse,
    date: LocalDate,
    sourceUnit: FinancialUnit,
    targetUnit: FinancialUnit,
): BigDecimal {
    if (sourceUnit == targetUnit) {
        return BigDecimal.ONE
    }
    // TODO(Johann) this error should be handled in API
    return response.getRate(sourceUnit, targetUnit, date)
        ?: error("No conversion rate found for $sourceUnit to $targetUnit on $date")
}