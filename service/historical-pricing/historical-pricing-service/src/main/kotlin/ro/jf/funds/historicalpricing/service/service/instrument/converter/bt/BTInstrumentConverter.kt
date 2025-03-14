package ro.jf.funds.historicalpricing.service.service.instrument.converter.bt

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import ro.jf.funds.historicalpricing.api.model.HistoricalPrice
import ro.jf.funds.historicalpricing.api.model.Instrument
import ro.jf.funds.historicalpricing.service.service.instrument.InstrumentConverter
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.text.NumberFormat.getNumberInstance
import java.time.format.DateTimeFormatter
import java.util.*
import java.time.LocalDate as JavaLocalDate

class BTInstrumentConverter(
    private val httpClient: HttpClient
) : InstrumentConverter {
    private val cache = mutableMapOf<Pair<Instrument, LocalDate>, HistoricalPrice>()
    private val localDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override suspend fun convert(instrument: Instrument, dates: List<LocalDate>): List<HistoricalPrice> =
        dates.map { date -> convert(instrument, date) }

    private suspend fun convert(instrument: Instrument, date: LocalDate): HistoricalPrice {
        cache[instrument to date]?.let { return it }
        putInCache(instrument, downloadHistoricalPrices(instrument))
        return cache[instrument to date] ?: error("Failed to convert $instrument at $date")
    }

    private fun putInCache(instrument: Instrument, historicalPrices: List<HistoricalPrice>) {
        if (historicalPrices.isEmpty()) return
        val firstPrice = historicalPrices.minBy { it.date }
        val pricesByDay = historicalPrices.associateBy { it.date }
        var fallbackValue = firstPrice
        generateSequence(firstPrice.date) { it + DatePeriod(days = 1) }
            .takeWhile { it < today() }
            .map { pricesByDay[it]?.also { fallbackValue = it } ?: HistoricalPrice(it, fallbackValue.price) }
            .forEach { cache[instrument to it.date] = it }
    }

    private suspend fun downloadHistoricalPrices(instrument: Instrument): List<HistoricalPrice> {
        val downloadSessionCookie = initiateDownloadSession(instrument)
        val downloadExcelInputStream = downloadExcelInputStream(downloadSessionCookie)
        return processExcelFile(downloadExcelInputStream)
    }

    suspend fun initiateDownloadSession(instrument: Instrument): String {
        val response = httpClient.post("https://www.btassetmanagement.ro/${instrument.symbol}") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(FormDataContent(Parameters.build {
                append("tip_titlu", "Valoare titlu")
            }))
            header("X-Requested-With", "XMLHttpRequest")
            header("X-October-Request-Handler", "onIstoric")
        }
        return response.headers["Set-Cookie"] ?: throw IllegalStateException("Failed to initiate download session.")
    }

    suspend fun downloadExcelInputStream(cookie: String): InputStream {
        val response = httpClient.get("https://www.btassetmanagement.ro/storage/app/media/download.xlsx") {
            header("Cookie", cookie)
        }
        return response.readBytes().let(::ByteArrayInputStream)
    }

    private fun processExcelFile(inputStream: InputStream): List<HistoricalPrice> {
        val sheet: Sheet = WorkbookFactory.create(inputStream).getSheetAt(0)
        return sheet.asSequence()
            .drop(1) // header
            .mapNotNull { row ->
                try {
                    HistoricalPrice(
                        date = row.getCell(0).toLocalDate(),
                        price = row.getCell(1).toBigDecimal()
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .toList()
    }

    private fun Cell.toBigDecimal(): BigDecimal {
        return when (cellType) {
            CellType.NUMERIC -> numericCellValue.toBigDecimal()
            CellType.STRING -> getNumberInstance(Locale.FRANCE).parse(stringCellValue).toDouble().toBigDecimal()
            else -> error("Unsupported cell type: $cellType")
        }
    }

    private fun Cell.toLocalDate(): LocalDate {
        return JavaLocalDate.parse(stringCellValue, localDateFormatter).let {
            LocalDate(it.year, it.monthValue, it.dayOfMonth)
        }
    }

    private fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
}