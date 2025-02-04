package ro.jf.funds.historicalpricing.api.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.FinancialUnit

@Serializable
data class ConversionRequest(
    val sourceUnit: FinancialUnit,
    val targetUnit: FinancialUnit,
    val date: LocalDate,
)

@Serializable
data class ConversionsRequest(
    val conversions: List<ConversionRequest>,
)
