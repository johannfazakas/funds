package ro.jf.funds.conversion.service.service.instrument.converter.financialtimes

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.datetime.LocalDate
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.service.domain.InstrumentConversionInfo
import ro.jf.funds.conversion.service.service.instrument.InstrumentConverter
import ro.jf.funds.conversion.service.service.instrument.converter.MonthlyCachedInstrumentConverterProxy
import ro.jf.funds.conversion.service.service.instrument.converter.financialtimes.model.FTCell
import ro.jf.funds.conversion.service.service.instrument.converter.financialtimes.model.FTHtmlResponse
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.*
import java.time.LocalDate as JavaLocalDate

class FinancialTimesInstrumentConverter(
    private val httpClient: HttpClient,
    private val cachedProxy: MonthlyCachedInstrumentConverterProxy = MonthlyCachedInstrumentConverterProxy(),
) : InstrumentConverter {
    // EEEE is Locale dependent, not supported by kotlin.
    private val cellFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    private val queryParamFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

    private suspend fun convert(instrument: InstrumentConversionInfo, date: LocalDate): ConversionResponse {
        return cachedProxy.getCachedOrConvert(instrument, date) { from, to ->
            convert(instrument, from, to)
        }
    }

    override suspend fun convert(instrument: InstrumentConversionInfo, dates: List<LocalDate>): List<ConversionResponse> =
        dates.map { date -> convert(instrument, date) }

    private suspend fun convert(instrument: InstrumentConversionInfo, from: LocalDate, to: LocalDate): List<ConversionResponse> {
        val response = httpClient
            .get("https://markets.ft.com/data/equities/ajax/get-historical-prices") {
                parameter("startDate", from.asQueryParam())
                parameter("endDate", to.asQueryParam())
                parameter("symbol", instrument.symbol)

            }
            .body<FTHtmlResponse>()
        val document: Document = Jsoup.parse(response.html)
        return document.body()
            .asCells()
            .chunkedAsPrices(instrument)
            .toList()
    }

    private fun Node.asCells(): Sequence<FTCell> {
        return when (this) {
            is Element -> childNodes().asSequence().flatMap { it: Node -> it.asCells() }
            is TextNode -> sequenceOf(asCell()).filterNotNull()
            else -> return emptySequence()
        }
    }

    private fun TextNode.asCell(): FTCell? = text().let { it.asDate() ?: it.asPrice() }

    private fun String.asDate(): FTCell.Date? {
        return try {
            JavaLocalDate.parse(this, cellFormatter).let {
                FTCell.Date(LocalDate(it.year, it.monthValue, it.dayOfMonth))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun String.asPrice(): FTCell.Price? {
        return try {
            val priceValue = NumberFormat.getNumberInstance(Locale.US).parse(this).toDouble()
            if (priceValue == 0.0) null else FTCell.Price(priceValue.toBigDecimal())
        } catch (e: Exception) {
            null
        }
    }

    private fun Sequence<FTCell>.chunkedAsPrices(instrument: InstrumentConversionInfo): Sequence<ConversionResponse> {
        return chunked(5) { (date, _, _, _, closedPrice) ->
            ConversionResponse(
                sourceUnit = instrument.instrument,
                targetCurrency = instrument.mainCurrency,
                date = (date as FTCell.Date).value,
                rate = (closedPrice as FTCell.Price).value
            )
        }
    }

    private fun LocalDate.asQueryParam(): String =
        queryParamFormatter.format(JavaLocalDate.of(year, monthNumber, dayOfMonth))
}
