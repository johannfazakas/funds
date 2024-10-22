package ro.jf.funds.historicalpricing.api.model

import kotlinx.serialization.Serializable

@Serializable
data class InstrumentConversionResponse(
    val instrument: Instrument,
    val currency: Currency,
    val historicalPrices: List<HistoricalPrice>
)
