package ro.jf.funds.historicalpricing.service.service.currency

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.historicalpricing.service.domain.CurrencyPairHistoricalPrice

// TODO(Johann) a bit too much, this repository interface might be removed
interface CurrencyPairHistoricalPriceRepository {
    suspend fun getHistoricalPrice(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        date: LocalDate,
    ): CurrencyPairHistoricalPrice?

    suspend fun getHistoricalPrices(
        sourceCurrency: Currency,
        targetCurrency: Currency,
        dates: List<LocalDate>,
    ): List<CurrencyPairHistoricalPrice>

    suspend fun saveHistoricalPrice(currencyPairHistoricalPrice: CurrencyPairHistoricalPrice)
}
