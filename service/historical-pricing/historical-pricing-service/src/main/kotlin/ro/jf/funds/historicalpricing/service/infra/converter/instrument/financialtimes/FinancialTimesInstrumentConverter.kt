package ro.jf.funds.historicalpricing.service.infra.converter.instrument.financialtimes

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.datetime.LocalDate
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import ro.jf.funds.historicalpricing.api.model.HistoricalPrice
import ro.jf.funds.historicalpricing.api.model.Instrument
import ro.jf.funds.historicalpricing.service.infra.converter.instrument.MonthlyCachedInstrumentConverterProxy
import ro.jf.funds.historicalpricing.service.infra.converter.instrument.financialtimes.model.FTCell
import ro.jf.funds.historicalpricing.service.infra.converter.instrument.financialtimes.model.FTHtmlResponse
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.*
import java.time.LocalDate as JavaLocalDate

class FinancialTimesInstrumentConverter(
    private val httpClient: HttpClient,
    private val cachedProxy: MonthlyCachedInstrumentConverterProxy = MonthlyCachedInstrumentConverterProxy()
) : ro.jf.funds.historicalpricing.service.domain.service.instrument.InstrumentConverter {
    private val cellFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    private val queryParamFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

    private suspend fun convert(instrument: Instrument, date: LocalDate): HistoricalPrice {
        return cachedProxy.getCachedOrConvert(instrument, date) { from, to ->
            convert(instrument, from, to)
        }
    }

    override suspend fun convert(instrument: Instrument, dates: List<LocalDate>): List<HistoricalPrice> =
        dates.map { date -> convert(instrument, date) }

    private suspend fun convert(instrument: Instrument, from: LocalDate, to: LocalDate): List<HistoricalPrice> {
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
            .chunkedAsPrices()
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

    private fun Sequence<FTCell>.chunkedAsPrices(): Sequence<HistoricalPrice> {
        return chunked(5) { (date, _, _, _, closedPrice) ->
            HistoricalPrice(
                date = (date as FTCell.Date).value,
                price = (closedPrice as FTCell.Price).value
            )
        }
    }

    private fun LocalDate.asQueryParam(): String =
        queryParamFormatter.format(JavaLocalDate.of(year, monthNumber, dayOfMonth))
}
