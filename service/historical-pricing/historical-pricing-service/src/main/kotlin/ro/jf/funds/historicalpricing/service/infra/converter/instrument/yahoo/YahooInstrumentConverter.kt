package ro.jf.funds.historicalpricing.service.infra.converter.instrument.yahoo

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.datetime.*
import ro.jf.funds.historicalpricing.api.model.HistoricalPrice
import ro.jf.funds.historicalpricing.api.model.Instrument
import ro.jf.funds.historicalpricing.service.infra.converter.instrument.MonthlyCachedInstrumentConverterProxy
import ro.jf.funds.historicalpricing.service.infra.converter.instrument.yahoo.model.YahooChartResponse
import java.math.BigDecimal

private const val ONE_DAY = "1d"

class YahooInstrumentConverter(
    private val httpClient: HttpClient,
    private val cachedProxy: MonthlyCachedInstrumentConverterProxy = MonthlyCachedInstrumentConverterProxy()
) : ro.jf.funds.historicalpricing.service.domain.service.instrument.InstrumentConverter {
    override suspend fun convert(instrument: Instrument, dates: List<LocalDate>): List<HistoricalPrice> =
        dates.map { date -> convert(instrument, date) }

    private suspend fun convert(instrument: Instrument, date: LocalDate): HistoricalPrice {
        return cachedProxy.getCachedOrConvert(instrument, date) { from, to ->
            convert(instrument, from, to)
        }
    }

    private suspend fun convert(
        instrument: Instrument,
        from: LocalDate,
        to: LocalDate
    ) = try {
        httpClient.get("https://query1.finance.yahoo.com/v8/finance/chart/${instrument.symbol}") {
            parameter("interval", ONE_DAY)
            parameter("period1", from.timestamp().toString())
            parameter("period2", to.timestamp().toString())
            parameter("symbol", instrument.symbol)
        }.body<YahooChartResponse>()
            .toHistoricalPrices()
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to fetch data for $instrument form $from to $to", e)
    }

    private fun YahooChartResponse.toHistoricalPrices(): List<HistoricalPrice> {
        val result = this.chart.result.first()
        val prices = result.indicators.quote.first().close

        return result.timestamp
            .mapIndexedNotNull { ix, timestamp ->
                prices[ix]?.let {
                    HistoricalPrice(
                        timestamp.toLocalDate(),
                        it
                    )
                }
            }
    }

    private fun YahooChartResponse.mappedPrices(
        instrument: Instrument,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<Pair<Instrument, LocalDate>, HistoricalPrice> {
        val result = this.chart.result.first()
        val prices = result.indicators.quote.first().close
        val timestampsToIndex = result.timestamp.mapIndexed { ix, timestamp -> timestamp to ix }.toMap()
        var fallbackPrice: BigDecimal = prices.filterNotNull().firstOrNull() ?: return emptyMap()

        return generateSequence(startDate) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= endDate }
            .associateWith { date ->
                timestampsToIndex[date.timestamp()]
                    ?.let { prices[it] }
                    ?.also { currentPrice -> fallbackPrice = currentPrice }
                    ?: fallbackPrice
            }
            .mapValues { (date, price) -> HistoricalPrice(date, price) }
            .mapKeys { (date, _) -> instrument to date }
    }

    private fun LocalDate.timestamp(): Long = atStartOfDayIn(TimeZone.UTC).epochSeconds
    private fun Long.toLocalDate() = Instant.fromEpochSeconds(this).toLocalDateTime(TimeZone.UTC).date
}
