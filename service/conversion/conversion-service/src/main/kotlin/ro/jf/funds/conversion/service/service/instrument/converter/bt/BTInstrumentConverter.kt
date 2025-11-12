package ro.jf.funds.conversion.service.service.instrument.converter.bt

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.service.domain.InstrumentConversionInfo
import ro.jf.funds.conversion.service.service.instrument.InstrumentConverter
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.text.NumberFormat.getNumberInstance
import java.util.*

class BTInstrumentConverter(
    private val httpClient: HttpClient,
) : InstrumentConverter {
    private val cache = mutableMapOf<Pair<InstrumentConversionInfo, LocalDate>, ConversionResponse>()

    @OptIn(FormatStringsInDatetimeFormats::class)
    private val localDateFormatter = LocalDate.Format { byUnicodePattern("yyyy-MM-dd") }

    override suspend fun convert(instrument: InstrumentConversionInfo, dates: List<LocalDate>): List<ConversionResponse> =
        dates.map { date -> convert(instrument, date) }

    private suspend fun convert(instrument: InstrumentConversionInfo, date: LocalDate): ConversionResponse {
        cache[instrument to date]?.let { return it }
        putInCache(instrument, downloadConversions(instrument))
        return cache[instrument to date] ?: error("Failed to convert $instrument at $date")
    }

    private fun putInCache(instrument: InstrumentConversionInfo, conversions: List<ConversionResponse>) {
        if (conversions.isEmpty()) return
        val firstPrice = conversions.minBy { it.date }
        val pricesByDay = conversions.associateBy { it.date }
        var fallbackValue = firstPrice
        generateSequence(firstPrice.date) { it + DatePeriod(days = 1) }
            .takeWhile { it < today() }
            .map { conversion ->
                pricesByDay[conversion]?.also { fallbackValue = it } ?: ConversionResponse(
                    instrument.instrument,
                    instrument.mainCurrency,
                    conversion,
                    fallbackValue.rate
                )
            }
            .forEach { cache[instrument to it.date] = it }
    }

    private suspend fun downloadConversions(instrument: InstrumentConversionInfo): List<ConversionResponse> {
        val downloadSessionCookie = initiateDownloadSession(instrument)
        val downloadExcelInputStream = downloadExcelInputStream(downloadSessionCookie)
        return processExcelFile(downloadExcelInputStream, instrument)
    }

    suspend fun initiateDownloadSession(instrument: InstrumentConversionInfo): String {
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
        return response.readRawBytes().let(::ByteArrayInputStream)
    }

    private fun processExcelFile(inputStream: InputStream, instrument: InstrumentConversionInfo): List<ConversionResponse> {
        val sheet: Sheet = WorkbookFactory.create(inputStream).getSheetAt(0)
        return sheet.asSequence()
            .drop(1) // header
            .mapNotNull { row ->
                try {
                    ConversionResponse(
                        date = row.getCell(0).toLocalDate(),
                        rate = row.getCell(1).toBigDecimal(),
                        sourceUnit = instrument.instrument,
                        targetCurrency = instrument.mainCurrency,
                    )
                } catch (_: Exception) {
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

    private fun Cell.toLocalDate(): LocalDate = localDateFormatter.parse(stringCellValue)

    private fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
}