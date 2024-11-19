package ro.jf.funds.importer.service.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.historicalpricing.api.model.HistoricalPrice
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.historicalpricing.api.model.Currency as HPCurrency

class HistoricalPricingAdapter(
    private val historicalPricingSdk: HistoricalPricingSdk,
) {
    suspend fun convertCurrencies(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>
    ): List<HistoricalPrice> {
        return withContext(Dispatchers.IO) {
            historicalPricingSdk.convertCurrency(
                sourceCurrency.toHistoricalPricingCurrency(),
                targetCurrency.toHistoricalPricingCurrency(),
                dates
            )
        }
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
