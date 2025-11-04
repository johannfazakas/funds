package ro.jf.funds.historicalpricing.service.service.instrument

import kotlinx.datetime.LocalDate
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.service.domain.PricingInstrument

fun interface InstrumentConverter {
    suspend fun convert(instrument: PricingInstrument, dates: List<LocalDate>): List<ConversionResponse>
}
