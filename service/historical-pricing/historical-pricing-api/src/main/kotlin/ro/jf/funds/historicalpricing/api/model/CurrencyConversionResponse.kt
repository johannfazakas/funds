package ro.jf.funds.historicalpricing.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.Currency

@Deprecated("Use ConversionResponse instead")
@Serializable
data class CurrencyConversionResponse(
    val sourceCurrency: Currency,
    val targetCurrency: Currency,
    val historicalPrices: List<HistoricalPrice>,
)
