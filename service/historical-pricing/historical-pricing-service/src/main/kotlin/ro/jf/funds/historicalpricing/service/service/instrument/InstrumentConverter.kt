package ro.jf.funds.historicalpricing.service.service.instrument

import kotlinx.datetime.LocalDate
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.api.model.Instrument

fun interface InstrumentConverter {
    suspend fun convert(instrument: Instrument, dates: List<LocalDate>): List<ConversionResponse>
}
