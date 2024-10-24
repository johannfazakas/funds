package ro.jf.funds.historicalpricing.api.model

import kotlinx.serialization.Serializable

@Serializable
data class CurrencyConversionResponse(
    val sourceCurrency: Currency,
    val targetCurrency: Currency,
    val historicalPrices: List<HistoricalPrice>
)
