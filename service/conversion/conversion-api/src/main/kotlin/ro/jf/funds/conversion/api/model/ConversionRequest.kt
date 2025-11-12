package ro.jf.funds.conversion.api.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit

@Serializable
data class ConversionRequest(
    val sourceUnit: FinancialUnit,
    val targetCurrency: Currency,
    val date: LocalDate,
)

@Serializable
data class ConversionsRequest(
    val conversions: List<ConversionRequest>,
) {
    fun isEmpty(): Boolean = conversions.isEmpty()
}
