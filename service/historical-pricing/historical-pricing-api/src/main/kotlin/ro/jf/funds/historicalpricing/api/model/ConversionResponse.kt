package ro.jf.funds.historicalpricing.api.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.serialization.BigDecimalSerializer
import java.math.BigDecimal

@Serializable
data class ConversionResponse(
    val sourceUnit: FinancialUnit,
    val targetUnit: FinancialUnit,
    val date: LocalDate,
    @Serializable(with = BigDecimalSerializer::class)
    val rate: BigDecimal,
)

@Serializable
data class ConversionsResponse(
    val conversions: List<ConversionResponse>,
) {
    companion object {
        fun empty() = ConversionsResponse(emptyList())
    }

    private val conversionsByRequest by lazy {
        conversions.associateBy({ ConversionRequest(it.sourceUnit, it.targetUnit, it.date) }, { it.rate })
    }

    fun getRate(sourceUnit: FinancialUnit, targetUnit: FinancialUnit, date: LocalDate): BigDecimal {
        return conversionsByRequest[ConversionRequest(sourceUnit, targetUnit, date)]
            ?: error("Rate not found for $sourceUnit to $targetUnit on $date.")
    }
}
