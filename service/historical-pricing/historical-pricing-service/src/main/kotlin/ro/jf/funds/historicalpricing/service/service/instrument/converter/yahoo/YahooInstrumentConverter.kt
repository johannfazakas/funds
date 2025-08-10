package ro.jf.funds.historicalpricing.service.service.instrument.converter.yahoo

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.datetime.*
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.api.model.Instrument
import ro.jf.funds.historicalpricing.service.service.instrument.InstrumentConverter
import ro.jf.funds.historicalpricing.service.service.instrument.converter.MonthlyCachedInstrumentConverterProxy
import ro.jf.funds.historicalpricing.service.service.instrument.converter.yahoo.model.YahooChartResponse

private const val ONE_DAY = "1d"

class YahooInstrumentConverter(
    private val httpClient: HttpClient,
    private val cachedProxy: MonthlyCachedInstrumentConverterProxy = MonthlyCachedInstrumentConverterProxy(),
) : InstrumentConverter {
    override suspend fun convert(instrument: Instrument, dates: List<LocalDate>): List<ConversionResponse> =
        dates.map { date -> convert(instrument, date) }

    private suspend fun convert(instrument: Instrument, date: LocalDate): ConversionResponse {
        return cachedProxy.getCachedOrConvert(instrument, date) { from, to ->
            convert(instrument, from, to)
        }
    }

    private suspend fun convert(
        instrument: Instrument,
        from: LocalDate,
        to: LocalDate,
    ) = try {
        httpClient.get("https://query1.finance.yahoo.com/v8/finance/chart/${instrument.symbol}") {
            parameter("interval", ONE_DAY)
            parameter("period1", from.timestamp().toString())
            parameter("period2", to.timestamp().toString())
            parameter("symbol", instrument.conversionSymbol)
        }.body<YahooChartResponse>()
            .toConversionResponses(instrument)
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to fetch data for $instrument form $from to $to", e)
    }

    private fun YahooChartResponse.toConversionResponses(instrument: Instrument): List<ConversionResponse> {
        val result = this.chart.result.first()
        val prices = result.indicators.quote.first().close

        return result.timestamp
            .mapIndexedNotNull { ix, timestamp ->
                prices[ix]?.let {
                    ConversionResponse(
                        instrument.symbol,
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
