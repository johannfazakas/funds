package ro.jf.funds.conversion.service.service.instrument

import kotlinx.datetime.LocalDate
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.service.domain.InstrumentConversionInfo

fun interface InstrumentConverter {
    suspend fun convert(instrument: InstrumentConversionInfo, dates: List<LocalDate>): List<ConversionResponse>
}
