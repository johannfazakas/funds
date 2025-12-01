package ro.jf.funds.conversion.api.model

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import ro.jf.funds.commons.api.model.Currency
import ro.jf.funds.commons.api.model.FinancialUnit
import ro.jf.funds.commons.api.serialization.BigDecimalSerializer

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
