package ro.jf.funds.conversion.service.domain

import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument

data class InstrumentConversionInfo(
    val instrument: Instrument,
    val source: InstrumentConversionSource,
    val symbol: String,
    val mainCurrency: Currency
)
