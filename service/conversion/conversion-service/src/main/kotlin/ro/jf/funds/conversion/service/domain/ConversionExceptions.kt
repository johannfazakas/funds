package ro.jf.funds.conversion.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.api.model.FinancialUnit
import ro.jf.funds.commons.api.model.Instrument

sealed class ConversionExceptions(message: String) : RuntimeException(message) {
    class ConversionNotFound(
        val sourceUnit: FinancialUnit, val targetUnit: FinancialUnit, val date: LocalDate,
    ) : ConversionExceptions("Conversion not found. sourceUnit = $sourceUnit, targetUnit = $targetUnit, date = $date.")

    class ConversionNotPermitted(
        val sourceUnit: FinancialUnit, val targetUnit: FinancialUnit,
    ) : ConversionExceptions("Conversion not permitted. sourceUnit = $sourceUnit, targetUnit = $targetUnit.")

    class ConversionIntegrationException(
        val api: String,
        val status: Int,
        val errorDetail: String,
    ) : ConversionExceptions("Conversion integration exception. api = $api, status = $status, errorDetail = $errorDetail.")

    class InstrumentSourceIntegrationNotFound(
        val instrument: Instrument,
    ) : ConversionExceptions("Instrument source integration not found. instrument = $instrument.")
}
