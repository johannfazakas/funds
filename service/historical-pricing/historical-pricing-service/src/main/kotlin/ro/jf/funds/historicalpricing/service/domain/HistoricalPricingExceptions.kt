package ro.jf.funds.historicalpricing.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Instrument

sealed class HistoricalPricingExceptions(message: String) : RuntimeException(message) {
    class HistoricalPriceNotFound(
        val sourceUnit: FinancialUnit, val targetUnit: FinancialUnit, val date: LocalDate,
    ) : HistoricalPricingExceptions("Historical price not found. sourceUnit = $sourceUnit, targetUnit = $targetUnit, date = $date.")

    class ConversionNotPermitted(
        val sourceUnit: FinancialUnit, val targetUnit: FinancialUnit,
    ) : HistoricalPricingExceptions("Conversion not permitted. sourceUnit = $sourceUnit, targetUnit = $targetUnit.")

    class HistoricalPricingIntegrationException(
        val api: String,
        val status: Int,
        val errorDetail: String,
    ) : HistoricalPricingExceptions("Historical pricing integration exception. api = $api, status = $status, errorDetail = $errorDetail.")

    class InstrumentSourceIntegrationNotFound(
        val instrument: Instrument,
    ) : HistoricalPricingExceptions("Instrument source integration not found. instrument = $instrument.")
}
