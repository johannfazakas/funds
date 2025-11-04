package ro.jf.funds.historicalpricing.service.domain

import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Instrument

data class PricingInstrument(
    val instrument: Instrument,
    val source: HistoricalPriceSource,
    val symbol: String,
    val mainCurrency: Currency
)
