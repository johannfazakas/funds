package ro.jf.funds.historicalpricing.service.service.instrument.converter

import kotlinx.datetime.*
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.service.domain.InstrumentConversionInfo

class MonthlyCachedInstrumentConverterProxy {
    private val cache = mutableMapOf<Pair<InstrumentConversionInfo, LocalDate>, ConversionResponse>()

    suspend fun getCachedOrConvert(
        instrument: InstrumentConversionInfo,
        date: LocalDate,
        historicalPricingProvider: suspend (from: LocalDate, to: LocalDate) -> List<ConversionResponse>,
    ): ConversionResponse {
        cache[instrument to date]?.let { return@getCachedOrConvert it }
        val startOfMonth = date.startOfMonth()
        val monthlyHistoricalPrices =
            historicalPricingProvider(startOfMonth, min(startOfMonth.endOfMonth(), today()))
        monthlyHistoricalPrices
            .fillGaps(instrument, startOfMonth)
            .map { (instrument to it.date) to it }
            .also(cache::putAll)
        return cache[instrument to date] ?: throw IllegalArgumentException("No price found for $date")
    }

    private fun List<ConversionResponse>.fillGaps(
        instrument: InstrumentConversionInfo,
        startOfMonth: LocalDate,
    ): List<ConversionResponse> {
        val prices = sortedBy { it.date }
            .associateBy { it.date }
        var fallbackPrice = prices.values.firstOrNull() ?: return emptyList()
        return generateSequence(startOfMonth) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= today() && it.month == startOfMonth.month }
            .map { date ->
                prices[date]?.also { fallbackPrice = it } ?: ConversionResponse(
                    instrument.instrument,
                    instrument.mainCurrency,
                    date,
                    fallbackPrice.rate
                )
            }
            .toList()
    }

    private fun LocalDate.startOfMonth(): LocalDate = LocalDate(year, monthNumber, 1)
    private fun LocalDate.endOfMonth(): LocalDate =
        startOfMonth().plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

    private fun min(a: LocalDate, b: LocalDate): LocalDate = if (a < b) a else b

    private fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date

}
