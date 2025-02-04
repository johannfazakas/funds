package ro.jf.funds.historicalpricing.service.service.instrument

import ro.jf.funds.historicalpricing.api.model.HistoricalPriceSource
import ro.jf.funds.historicalpricing.api.model.Instrument

class InstrumentConverterRegistry(
    private val instrumentConverterBySource: Map<HistoricalPriceSource, InstrumentConverter>
) {
    fun getConverter(instrument: Instrument): InstrumentConverter =
        instrumentConverterBySource[instrument.source] ?: error("No strategy found for converter $instrument")
}
