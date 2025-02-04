package ro.jf.funds.historicalpricing.service.service.instrument.converter

import kotlinx.datetime.*
import ro.jf.funds.historicalpricing.api.model.HistoricalPrice
import ro.jf.funds.historicalpricing.api.model.Instrument

class MonthlyCachedInstrumentConverterProxy {
    private val cache = mutableMapOf<Pair<Instrument, LocalDate>, HistoricalPrice>()

    suspend fun getCachedOrConvert(
        instrument: Instrument,
        date: LocalDate,
        historicalPricingProvider: suspend (from: LocalDate, to: LocalDate) -> List<HistoricalPrice>
    ): HistoricalPrice {
        cache[instrument to date]?.let { return@getCachedOrConvert it }
        val startOfMonth = date.startOfMonth()
        val monthlyHistoricalPrices =
            historicalPricingProvider(startOfMonth, min(startOfMonth.endOfMonth(), today()))
        monthlyHistoricalPrices
            .fillGaps(startOfMonth)
            .map { (instrument to it.date) to it }
            .also(cache::putAll)
        return cache[instrument to date] ?: throw IllegalArgumentException("No price found for $date")
    }

    private fun List<HistoricalPrice>.fillGaps(startOfMonth: LocalDate): List<HistoricalPrice> {
        val prices = sortedBy { it.date }
            .associateBy { it.date }
        var fallbackPrice = prices.values.firstOrNull() ?: return emptyList()
        return generateSequence(startOfMonth) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= today() && it.month == startOfMonth.month }
            .map { date -> prices[date]?.also { fallbackPrice = it } ?: HistoricalPrice(date, fallbackPrice.price) }
            .toList()
    }

    private fun LocalDate.startOfMonth(): LocalDate = LocalDate(year, monthNumber, 1)
    private fun LocalDate.endOfMonth(): LocalDate =
        startOfMonth().plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

    private fun min(a: LocalDate, b: LocalDate): LocalDate = if (a < b) a else b

    private fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date

}