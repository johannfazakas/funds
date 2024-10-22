package ro.jf.funds.historicalpricing.service.domain.service.instrument

import ro.jf.funds.historicalpricing.api.model.HistoricalPriceSource
import ro.jf.funds.historicalpricing.api.model.Instrument

class InstrumentConverterRegistry(
    private val instrumentConverterBySource: Map<HistoricalPriceSource, ro.jf.funds.historicalpricing.service.domain.service.instrument.InstrumentConverter>
) {
    fun getConverter(instrument: Instrument): ro.jf.funds.historicalpricing.service.domain.service.instrument.InstrumentConverter =
        instrumentConverterBySource[instrument.source] ?: error("No converter found for instrument $instrument")
}
