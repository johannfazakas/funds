package ro.jf.funds.historicalpricing.service.service.instrument

import ro.jf.funds.historicalpricing.api.model.HistoricalPriceSource
import ro.jf.funds.historicalpricing.api.model.PricingInstrument

class InstrumentConverterRegistry(
    private val instrumentConverterBySource: Map<HistoricalPriceSource, InstrumentConverter>
) {
    fun getConverter(instrument: PricingInstrument): InstrumentConverter =
        instrumentConverterBySource[instrument.source] ?: error("No strategy found for converter $instrument")
}
