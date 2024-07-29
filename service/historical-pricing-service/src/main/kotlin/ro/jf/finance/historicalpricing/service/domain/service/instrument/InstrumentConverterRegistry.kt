package ro.jf.finance.historicalpricing.service.domain.service.instrument

import ro.jf.bk.historicalpricing.api.model.HistoricalPriceSource
import ro.jf.bk.historicalpricing.api.model.Instrument

class InstrumentConverterRegistry(
    private val instrumentConverterBySource: Map<HistoricalPriceSource, InstrumentConverter>
) {
    fun getConverter(instrument: Instrument): InstrumentConverter =
        instrumentConverterBySource[instrument.source] ?: error("No converter found for instrument $instrument")
}
