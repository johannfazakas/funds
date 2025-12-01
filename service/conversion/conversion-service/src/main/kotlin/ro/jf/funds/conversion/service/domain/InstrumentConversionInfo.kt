package ro.jf.funds.conversion.service.domain

import ro.jf.funds.commons.api.model.Currency
import ro.jf.funds.commons.api.model.Instrument

data class InstrumentConversionInfo(
    val instrument: Instrument,
    val source: InstrumentConversionSource,
    val symbol: String,
    val mainCurrency: Currency
)
