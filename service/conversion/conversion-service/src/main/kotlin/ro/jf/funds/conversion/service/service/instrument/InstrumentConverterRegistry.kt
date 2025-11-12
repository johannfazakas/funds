package ro.jf.funds.conversion.service.service.instrument

import ro.jf.funds.conversion.service.domain.InstrumentConversionSource
import ro.jf.funds.conversion.service.domain.InstrumentConversionInfo

class InstrumentConverterRegistry(
    private val instrumentConverterBySource: Map<InstrumentConversionSource, InstrumentConverter>
) {
    fun getConverter(instrument: InstrumentConversionInfo): InstrumentConverter =
        instrumentConverterBySource[instrument.source] ?: error("No strategy found for converter $instrument")
}
