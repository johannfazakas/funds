package ro.jf.funds.conversion.service.service.instrument.converter.yahoo

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.datetime.*
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.service.domain.InstrumentConversionInfo
import ro.jf.funds.conversion.service.service.instrument.InstrumentConverter
import ro.jf.funds.conversion.service.service.instrument.converter.MonthlyCachedInstrumentConverterProxy
import ro.jf.funds.conversion.service.service.instrument.converter.yahoo.model.YahooChartResponse

private const val ONE_DAY = "1d"

class YahooInstrumentConverter(
    private val httpClient: HttpClient,
    private val cachedProxy: MonthlyCachedInstrumentConverterProxy = MonthlyCachedInstrumentConverterProxy(),
) : InstrumentConverter {
    override suspend fun convert(instrument: InstrumentConversionInfo, dates: List<LocalDate>): List<ConversionResponse> =
        dates.mapNotNull { date -> convert(instrument, date) }

    private suspend fun convert(instrument: InstrumentConversionInfo, date: LocalDate): ConversionResponse? {
        return cachedProxy.getCachedOrConvert(instrument, date) { from, to ->
            convert(instrument, from, to)
        }
    }

    private suspend fun convert(
        instrument: InstrumentConversionInfo,
        from: LocalDate,
        to: LocalDate,
    ) = try {
        httpClient.get("https://query1.finance.yahoo.com/v8/finance/chart/${instrument.symbol}") {
            parameter("interval", ONE_DAY)
            parameter("period1", from.timestamp().toString())
            parameter("period2", to.timestamp().toString())
            parameter("symbol", instrument.symbol)
        }.body<YahooChartResponse>()
            .toConversionResponses(instrument)
    } catch (e: Exception) {
        throw IllegalArgumentException(
            "Failed to fetch reportdata for $instrument from $from (${from.timestamp()}) to $to (${to.timestamp()})",
            e
        )
    }

    private fun YahooChartResponse.toConversionResponses(instrument: InstrumentConversionInfo): List<ConversionResponse> {
        val result = this.chart.result.firstOrNull() ?: return emptyList()
        val prices = result.indicators.quote.firstOrNull()?.close ?: return emptyList()
        if (result.timestamp.isEmpty()) return emptyList()

        return result.timestamp
            .mapIndexedNotNull { ix, timestamp ->
                prices.getOrNull(ix)?.let {
                    ConversionResponse(
                        instrument.instrument,
                        instrument.mainCurrency,
                        timestamp.toLocalDate(),
                        it
                    )
                }
            }
    }

    private fun LocalDate.timestamp(): Long = atStartOfDayIn(TimeZone.UTC).epochSeconds
    private fun Long.toLocalDate() = Instant.fromEpochSeconds(this).toLocalDateTime(TimeZone.UTC).date
}
