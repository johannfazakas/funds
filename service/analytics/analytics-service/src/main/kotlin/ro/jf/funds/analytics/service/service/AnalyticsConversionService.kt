package ro.jf.funds.analytics.service.service

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toJavaBigDecimal
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.service.domain.InvestmentPosition
import ro.jf.funds.analytics.service.domain.UnitAmounts
import ro.jf.funds.conversion.api.model.ConversionRequest
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit

private val log = logger { }

class AnalyticsConversionService(
    private val conversionSdk: ConversionSdk,
) {
    suspend fun convertAmounts(amounts: UnitAmounts, targetCurrency: Currency, date: LocalDate): BigDecimal {
        if (amounts.units.isEmpty()) return BigDecimal.ZERO
        val request = ConversionsRequest(amounts.units.map { ConversionRequest(it, targetCurrency, date) })
        val rates = conversionSdk.convert(request)
        return amounts.entries.fold(BigDecimal.ZERO) { acc, (unit, amount) ->
            val rate = rates.getRate(unit, targetCurrency, date)
            if (rate == null) {
                log.warn { "Conversion rate not found for $unit -> $targetCurrency on $date, treating as zero" }
                return@fold acc
            }
            acc + amount * rate
        }
    }

    suspend fun convertPositionsAtHistoricalCost(
        positions: List<InvestmentPosition>, targetCurrency: Currency,
    ): BigDecimal {
        if (positions.isEmpty()) return BigDecimal.ZERO
        val conversionKeys = positions.map { Triple(it.currencyUnit, targetCurrency, it.date) }.distinct()
        val request = ConversionsRequest(conversionKeys.map { (source, target, date) ->
            ConversionRequest(source, target, date)
        })
        val rates = conversionSdk.convert(request)
        return positions.fold(BigDecimal.ZERO) { acc, position ->
            val rate = rates.getRate(position.currencyUnit, targetCurrency, position.date)
            if (rate == null) {
                log.warn { "Conversion rate not found for ${position.currencyUnit} -> $targetCurrency on ${position.date}, treating as zero" }
                return@fold acc
            }
            acc + position.currencyAmount.negate() * rate
        }
    }

    suspend fun rateOrOne(source: FinancialUnit, target: Currency, date: LocalDate): java.math.BigDecimal {
        val response = conversionSdk.convert(ConversionsRequest(listOf(ConversionRequest(source, target, date))))
        return response.getRate(source, target, date)?.toJavaBigDecimal() ?: java.math.BigDecimal.ONE
    }
}
