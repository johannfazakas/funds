package ro.jf.funds.importer.service.service

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import java.math.BigDecimal
import ro.jf.funds.historicalpricing.api.model.Currency as HPCurrency

class HistoricalPricingAdapter(
    private val historicalPricingSdk: HistoricalPricingSdk,
) {
    suspend fun convertCurrency(sourceCurrency: Currency, targetCurrency: Currency, date: LocalDate): BigDecimal {
        // TODO(Johann) should delegate call to Dispatchers.IO or use ktor client in the sdk
        return historicalPricingSdk.convertCurrency(
            sourceCurrency.toHistoricalPricingCurrency(),
            targetCurrency.toHistoricalPricingCurrency(),
            date
        ).price
    }

    // TODO(Johann) historical pricing should also use the same currency model
    private fun Currency.toHistoricalPricingCurrency(): HPCurrency {
        return when (this) {
            Currency.RON -> HPCurrency.RON
            Currency.EUR -> HPCurrency.EUR
            else -> throw ImportDataException("Currency not supported: $this")
        }
    }
}
