package ro.jf.funds.historicalpricing.service.service.instrument

import ro.jf.funds.historicalpricing.service.domain.HistoricalPriceSource
import ro.jf.funds.historicalpricing.service.domain.PricingInstrument

class InstrumentConverterRegistry(
    private val instrumentConverterBySource: Map<HistoricalPriceSource, InstrumentConverter>
) {
    fun getConverter(instrument: PricingInstrument): InstrumentConverter =
        instrumentConverterBySource[instrument.source] ?: error("No strategy found for converter $instrument")
}
