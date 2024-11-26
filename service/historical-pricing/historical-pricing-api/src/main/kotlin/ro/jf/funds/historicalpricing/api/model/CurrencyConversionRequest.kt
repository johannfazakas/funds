package ro.jf.funds.historicalpricing.api.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.Currency

@Serializable
data class CurrencyConversionRequest(
    val sourceCurrency: Currency,
    val targetCurrency: Currency,
    val dates: List<LocalDate>
)
