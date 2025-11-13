package ro.jf.funds.conversion.api.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.serialization.BigDecimalSerializer
import java.math.BigDecimal

@Serializable
data class ConversionResponse(
    val sourceUnit: FinancialUnit,
    val targetCurrency: Currency,
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
        conversions.associateBy({ ConversionRequest(it.sourceUnit, it.targetCurrency, it.date) }, { it.rate })
    }

    fun getRate(sourceUnit: FinancialUnit, targetCurrency: Currency, date: LocalDate): BigDecimal? {
        return conversionsByRequest[ConversionRequest(sourceUnit, targetCurrency, date)]
    }
}
