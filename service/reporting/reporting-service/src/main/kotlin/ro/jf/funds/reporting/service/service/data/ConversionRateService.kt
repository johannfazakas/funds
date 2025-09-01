package ro.jf.funds.reporting.service.service.data

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
import java.util.*

// TODO(Johann) this logic could be entirely handled by the SDK actually
class ConversionRateService(
    private val historicalPricingSdk: HistoricalPricingSdk,
) {
    // TODO(Johann) this will grow indefinitely. what other options do I have for an in memory cache? I would like old data to be removed
    private val cache: MutableMap<Pair<UUID, ConversionRequest>, BigDecimal> = mutableMapOf()
    private val cacheWriteMutex = Mutex()

    suspend fun getRate(
        userId: UUID,
        date: LocalDate,
        // TODO(Johann) handle same currency case, maybe it shouldn't send a request
        sourceUnit: FinancialUnit,
        targetUnit: FinancialUnit,
    ): BigDecimal {
        val conversionRequest = ConversionRequest(sourceUnit, targetUnit, date)
        val cachedRate = cache[Pair(userId, conversionRequest)]
        if (cachedRate != null) return cachedRate
        retrieveAndCacheMonthlyRates(userId, date.year, date.month, sourceUnit, targetUnit)
        return cache[Pair(userId, conversionRequest)]
//            TODO(Johann) an api translated error should be used in this case
            ?: error("No conversion rate found for $sourceUnit to $targetUnit on $date")
    }

    suspend fun retrieveAndCacheMonthlyRates(
        userId: UUID, year: Int, month: Month, sourceUnit: FinancialUnit, targetUnit: FinancialUnit,
    ) {
        val conversionRequests = generateSequence(
            LocalDate(year, month, 1)
        ) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it.month == month }
            .map { ConversionRequest(sourceUnit, targetUnit, it) }
            .toList()
        val conversions = historicalPricingSdk.convert(userId, ConversionsRequest(conversionRequests))
        cacheWriteMutex.withLock {
            conversions.conversions.forEach {
                cache[Pair(userId, ConversionRequest(it.sourceUnit, it.targetUnit, it.date))] = it.rate
            }
        }
    }
}
