package ro.jf.funds.historicalpricing.service.service.instrument

import kotlinx.datetime.LocalDate
import ro.jf.funds.historicalpricing.service.domain.InstrumentHistoricalPrice

interface InstrumentHistoricalPriceRepository {
    suspend fun getHistoricalPrices(
        symbol: String,
        currency: String,
        date: LocalDate
    ): InstrumentHistoricalPrice?

    suspend fun getHistoricalPrices(
        symbol: String,
        currency: String,
        dates: List<LocalDate>
    ): List<InstrumentHistoricalPrice>

    suspend fun saveHistoricalPrice(instrumentHistoricalPrice: InstrumentHistoricalPrice)
}
