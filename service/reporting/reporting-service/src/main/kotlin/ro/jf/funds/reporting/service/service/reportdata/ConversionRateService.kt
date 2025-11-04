package ro.jf.funds.reporting.service.service.reportdata

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import java.math.BigDecimal

class ConversionRateService(
    private val historicalPricingSdk: HistoricalPricingSdk,
) {
    private val cache: MutableMap<ConversionRequest, BigDecimal> = mutableMapOf()
    private val cacheWriteMutex = Mutex()

    suspend fun getRate(
        date: LocalDate,
        sourceUnit: FinancialUnit,
        targetUnit: FinancialUnit,
    ): BigDecimal {
        val conversionRequest = ConversionRequest(sourceUnit, targetUnit, date)
        val cachedRate = cache[conversionRequest]
        if (cachedRate != null) return cachedRate
        retrieveAndCacheMonthlyRates(date.year, date.month, sourceUnit, targetUnit)
        return cache[conversionRequest]
            ?: error("No conversion rate found for $sourceUnit to $targetUnit on $date")
    }

    suspend fun retrieveAndCacheMonthlyRates(
        year: Int, month: Month, sourceUnit: FinancialUnit, targetUnit: FinancialUnit,
    ) {
        val conversionRequests = generateSequence(
            LocalDate(year, month, 1)
        ) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it.month == month }
            .map { ConversionRequest(sourceUnit, targetUnit, it) }
            .toList()
        val conversions = historicalPricingSdk.convert(ConversionsRequest(conversionRequests))
        cacheWriteMutex.withLock {
            conversions.conversions.forEach {
                cache[ConversionRequest(it.sourceUnit, it.targetUnit, it.date)] = it.rate
            }
        }
    }
}
