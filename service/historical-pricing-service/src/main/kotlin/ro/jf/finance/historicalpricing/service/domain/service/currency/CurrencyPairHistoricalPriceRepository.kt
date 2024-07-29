package ro.jf.finance.historicalpricing.service.domain.service.currency

import kotlinx.datetime.LocalDate
import ro.jf.finance.historicalpricing.service.domain.model.CurrencyPairHistoricalPrice

interface CurrencyPairHistoricalPriceRepository {
    suspend fun getHistoricalPrice(
        sourceCurrency: String,
        targetCurrency: String,
        date: LocalDate
    ): CurrencyPairHistoricalPrice?

    suspend fun getHistoricalPrices(
        sourceCurrency: String,
        targetCurrency: String,
        dates: List<LocalDate>
    ): List<CurrencyPairHistoricalPrice>

    suspend fun saveHistoricalPrice(currencyPairHistoricalPrice: CurrencyPairHistoricalPrice)
}
