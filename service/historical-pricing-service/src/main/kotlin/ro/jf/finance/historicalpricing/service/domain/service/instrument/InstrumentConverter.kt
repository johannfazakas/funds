package ro.jf.finance.historicalpricing.service.domain.service.instrument

import kotlinx.datetime.LocalDate
import ro.jf.bk.historicalpricing.api.model.HistoricalPrice
import ro.jf.bk.historicalpricing.api.model.Instrument

fun interface InstrumentConverter {
    suspend fun convert(instrument: Instrument, dates: List<LocalDate>): List<HistoricalPrice>
}
