package ro.jf.funds.historicalpricing.service.domain

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Instrument

data class InstrumentConversionInfo(
    val instrument: Instrument,
    val source: InstrumentConversionSource,
    val symbol: String,
    val mainCurrency: Currency
)
