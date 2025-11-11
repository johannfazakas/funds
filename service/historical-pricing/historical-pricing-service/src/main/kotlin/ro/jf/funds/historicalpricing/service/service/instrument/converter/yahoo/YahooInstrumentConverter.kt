package ro.jf.funds.historicalpricing.service.service.instrument.converter.yahoo

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.datetime.*
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.service.domain.InstrumentConversionInfo
import ro.jf.funds.historicalpricing.service.service.instrument.InstrumentConverter
import ro.jf.funds.historicalpricing.service.service.instrument.converter.MonthlyCachedInstrumentConverterProxy
import ro.jf.funds.historicalpricing.service.service.instrument.converter.yahoo.model.YahooChartResponse
import java.math.BigDecimal

private const val ONE_DAY = "1d"

class YahooInstrumentConverter(
    private val httpClient: HttpClient,
    private val cachedProxy: MonthlyCachedInstrumentConverterProxy = MonthlyCachedInstrumentConverterProxy(),
) : InstrumentConverter {
    override suspend fun convert(instrument: InstrumentConversionInfo, dates: List<LocalDate>): List<ConversionResponse> =
        dates.map { date -> checkHardcodedValues(instrument, date) ?: convert(instrument, date) }

    private suspend fun convert(instrument: InstrumentConversionInfo, date: LocalDate): ConversionResponse {
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
        val result = this.chart.result.first()
        val prices = result.indicators.quote.first().close

        return result.timestamp
            .mapIndexedNotNull { ix, timestamp ->
                prices[ix]?.let {
                    ConversionResponse(
                        instrument.instrument,
                        instrument.mainCurrency,
                        timestamp.toLocalDate(),
                        it
                    )
                }
            }
    }

    // IMAE doesn't have values for 2022 and I couldn't find a library/api that could provide it
    private fun checkHardcodedValues(instrument: InstrumentConversionInfo, date: LocalDate): ConversionResponse? {
        if (instrument.instrument.value == "IMAE") {
            if (date.year == 2022) {
                val rate = when (date.month) {
                    Month.MAY -> BigDecimal("63.54")
                    Month.JUNE -> BigDecimal("63.90")
                    Month.JULY -> BigDecimal("59.025")
                    Month.AUGUST -> BigDecimal("63.43")
                    Month.SEPTEMBER -> BigDecimal("59.94")
                    Month.OCTOBER -> BigDecimal("56.56")
                    Month.NOVEMBER -> BigDecimal("60.34")
                    Month.DECEMBER -> BigDecimal("64.725")
                    else -> null
                }
                if (rate != null) {
                    return ConversionResponse(instrument.instrument, instrument.mainCurrency, date, rate)
                }
            }
        }
        return null
    }

    private fun LocalDate.timestamp(): Long = atStartOfDayIn(TimeZone.UTC).epochSeconds
    private fun Long.toLocalDate() = Instant.fromEpochSeconds(this).toLocalDateTime(TimeZone.UTC).date
}
