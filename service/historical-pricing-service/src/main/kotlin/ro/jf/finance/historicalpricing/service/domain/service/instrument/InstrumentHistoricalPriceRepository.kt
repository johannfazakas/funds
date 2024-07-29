package ro.jf.finance.historicalpricing.service.domain.service.instrument

import kotlinx.datetime.LocalDate
import ro.jf.finance.historicalpricing.service.domain.model.InstrumentHistoricalPrice

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
