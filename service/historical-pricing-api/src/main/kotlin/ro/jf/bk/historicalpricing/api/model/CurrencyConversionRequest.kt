package ro.jf.bk.historicalpricing.api.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class CurrencyConversionRequest(
    val sourceCurrency: Currency,
    val targetCurrency: Currency,
    val dates: List<LocalDate>
)
