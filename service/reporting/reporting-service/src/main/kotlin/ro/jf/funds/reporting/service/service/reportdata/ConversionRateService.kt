package ro.jf.funds.reporting.service.service.reportdata

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.conversion.api.model.ConversionRequest
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.sdk.ConversionSdk
import com.ionspin.kotlin.bignum.decimal.toJavaBigDecimal
import java.math.BigDecimal

class ConversionRateService(
    private val conversionSdk: ConversionSdk,
) {
    private val cache: MutableMap<ConversionRequest, BigDecimal> = mutableMapOf()
    private val cacheWriteMutex = Mutex()

    suspend fun getRate(
        date: LocalDate,
        sourceUnit: FinancialUnit,
        targetCurrency: Currency,
    ): BigDecimal {
        val conversionRequest = ConversionRequest(sourceUnit, targetCurrency, date)
        val cachedRate = cache[conversionRequest]
        if (cachedRate != null) return cachedRate
        retrieveAndCacheMonthlyRates(date.year, date.month, sourceUnit, targetCurrency)
        return cache[conversionRequest]
            ?: error("No conversion rate found for $sourceUnit to $targetCurrency on $date")
    }

    suspend fun retrieveAndCacheMonthlyRates(
        year: Int, month: Month, sourceUnit: FinancialUnit, targetCurrency: Currency,
    ) {
        val conversionRequests = generateSequence(
            LocalDate(year, month, 1)
        ) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it.month == month }
            .map { ConversionRequest(sourceUnit, targetCurrency, it) }
            .toList()
        val conversions = conversionSdk.convert(ConversionsRequest(conversionRequests))
        cacheWriteMutex.withLock {
            conversions.conversions.forEach {
                cache[ConversionRequest(it.sourceUnit, it.targetCurrency, it.date)] = it.rate.toJavaBigDecimal()
            }
        }
    }
}
