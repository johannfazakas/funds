package ro.jf.funds.historicalpricing.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Instrument
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.service.domain.Conversion
import ro.jf.funds.historicalpricing.service.domain.InstrumentConversionSource
import ro.jf.funds.historicalpricing.service.domain.InstrumentConversionInfo
import ro.jf.funds.historicalpricing.service.persistence.ConversionRepository
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyConverter
import ro.jf.funds.historicalpricing.service.service.instrument.InstrumentConverter
import ro.jf.funds.historicalpricing.service.service.instrument.InstrumentConverterRegistry
import ro.jf.funds.historicalpricing.service.service.instrument.InstrumentConversionInfoRepository
import java.math.BigDecimal

class ConversionServiceTest {
    private val currencyConverter = mock<CurrencyConverter>()
    private val conversionRepository = mock<ConversionRepository>()
    private val instrumentConverterRegistry = mock<InstrumentConverterRegistry>()
    private val instrumentConversionInfoRepository = mock<InstrumentConversionInfoRepository>()

    private val conversionService = ConversionService(
        conversionRepository,
        currencyConverter,
        instrumentConversionInfoRepository,
        instrumentConverterRegistry
    )

    private val date1 = LocalDate.parse("2025-02-01")
    private val date2 = LocalDate.parse("2025-02-02")
    private val date3 = LocalDate.parse("2025-02-03")

    @Test
    fun `should return saved conversions when available`(): Unit = runBlocking {
        whenever(
            conversionRepository.getHistoricalPrices(
                Currency.RON,
                Currency.EUR,
                listOf(date1, date2)
            )
        )
            .thenReturn(
                listOf(
                    Conversion(Currency.RON, Currency.EUR, date1, BigDecimal("0.2")),
                    Conversion(Currency.RON, Currency.EUR, date2, BigDecimal("0.21"))
                )
            )
        whenever(conversionRepository.getHistoricalPrices(Currency.EUR, Currency.RON, listOf(date3)))
            .thenReturn(
                listOf(
                    Conversion(Currency.EUR, Currency.RON, date3, BigDecimal("4.9"))
                )
            )
        val request = ConversionsRequest(
            listOf(
                ConversionRequest(Currency.RON, Currency.EUR, date1),
                ConversionRequest(Currency.RON, Currency.EUR, date2),
                ConversionRequest(Currency.EUR, Currency.RON, date3)
            )
        )

        val response = conversionService.convert(request)

        assertThat(response.conversions).hasSize(3)
        assertThat(response.getRate(Currency.RON, Currency.EUR, date1)).isEqualTo(BigDecimal("0.2"))
        assertThat(response.getRate(Currency.RON, Currency.EUR, date2)).isEqualTo(BigDecimal("0.21"))
        assertThat(response.getRate(Currency.EUR, Currency.RON, date3)).isEqualTo(BigDecimal("4.9"))
        assertThatThrownBy {
            assertThat(response.getRate(Currency.RON, Currency.EUR, date3))
        }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `should return and save new conversions when not available in storage`(): Unit = runBlocking {
        whenever(
            conversionRepository.getHistoricalPrices(
                Currency.RON,
                Currency.EUR,
                listOf(date1, date2)
            )
        )
            .thenReturn(
                listOf(
                    Conversion(Currency.RON, Currency.EUR, date1, BigDecimal("0.2")),
                )
            )
        whenever(conversionRepository.getHistoricalPrices(Currency.EUR, Currency.RON, listOf(date3)))
            .thenReturn(emptyList())
        whenever(currencyConverter.convert(Currency.RON, Currency.EUR, listOf(date2)))
            .thenReturn(listOf(ConversionResponse(Currency.RON, Currency.EUR, date2, BigDecimal("0.21"))))
        whenever(currencyConverter.convert(Currency.EUR, Currency.RON, listOf(date3)))
            .thenReturn(listOf(ConversionResponse(Currency.EUR, Currency.RON, date3, BigDecimal("4.9"))))

        val request = ConversionsRequest(
            listOf(
                ConversionRequest(Currency.RON, Currency.EUR, date1),
                ConversionRequest(Currency.RON, Currency.EUR, date2),
                ConversionRequest(Currency.EUR, Currency.RON, date3)
            )
        )

        val response = conversionService.convert(request)

        assertThat(response.conversions).hasSize(3)
        assertThat(response.getRate(Currency.RON, Currency.EUR, date1)).isEqualTo(BigDecimal("0.2"))
        assertThat(response.getRate(Currency.RON, Currency.EUR, date2)).isEqualTo(BigDecimal("0.21"))
        assertThat(response.getRate(Currency.EUR, Currency.RON, date3)).isEqualTo(BigDecimal("4.9"))
        assertThatThrownBy {
            assertThat(response.getRate(Currency.RON, Currency.EUR, date3))
        }
    }

    @Test
    fun `given instrument to main currency conversion when partially stored then should combine stored and fetched conversions`(): Unit = runBlocking {
        val instrument = Instrument("VWCE")
        val pricingInstrument = InstrumentConversionInfo(
            instrument = instrument,
            source = InstrumentConversionSource.FINANCIAL_TIMES,
            symbol = "VWCE",
            mainCurrency = Currency.EUR
        )
        val instrumentConverter = mock<InstrumentConverter>()

        whenever(instrumentConversionInfoRepository.findByInstrument(instrument))
            .thenReturn(pricingInstrument)
        whenever(conversionRepository.getHistoricalPrices(instrument, Currency.EUR, listOf(date1, date2, date3)))
            .thenReturn(
                listOf(
                    Conversion(instrument, Currency.EUR, date1, BigDecimal("100.5"))
                )
            )
        whenever(instrumentConverterRegistry.getConverter(pricingInstrument))
            .thenReturn(instrumentConverter)
        whenever(instrumentConverter.convert(pricingInstrument, listOf(date2, date3)))
            .thenReturn(
                listOf(
                    ConversionResponse(instrument, Currency.EUR, date2, BigDecimal("101.2")),
                    ConversionResponse(instrument, Currency.EUR, date3, BigDecimal("102.8"))
                )
            )

        val request = ConversionsRequest(
            listOf(
                ConversionRequest(instrument, Currency.EUR, date1),
                ConversionRequest(instrument, Currency.EUR, date2),
                ConversionRequest(instrument, Currency.EUR, date3)
            )
        )

        val response = conversionService.convert(request)

        assertThat(response.conversions).hasSize(3)
        assertThat(response.getRate(instrument, Currency.EUR, date1)).isEqualTo(BigDecimal("100.5"))
        assertThat(response.getRate(instrument, Currency.EUR, date2)).isEqualTo(BigDecimal("101.2"))
        assertThat(response.getRate(instrument, Currency.EUR, date3)).isEqualTo(BigDecimal("102.8"))
    }

    @Test
    fun `given instrument to different currency conversion when partially stored then should apply implicit currency conversion`(): Unit = runBlocking {
        val instrument = Instrument("VWCE")
        val pricingInstrument = InstrumentConversionInfo(
            instrument = instrument,
            source = InstrumentConversionSource.FINANCIAL_TIMES,
            symbol = "VWCE",
            mainCurrency = Currency.EUR
        )
        val instrumentConverter = mock<InstrumentConverter>()

        whenever(instrumentConversionInfoRepository.findByInstrument(instrument))
            .thenReturn(pricingInstrument)
        whenever(conversionRepository.getHistoricalPrices(instrument, Currency.RON, listOf(date1, date2)))
            .thenReturn(
                listOf(
                    Conversion(instrument, Currency.RON, date1, BigDecimal("502.5"))
                )
            )
        whenever(conversionRepository.getHistoricalPrices(Currency.EUR, Currency.RON, listOf(date2)))
            .thenReturn(emptyList())
        whenever(currencyConverter.convert(Currency.EUR, Currency.RON, listOf(date2)))
            .thenReturn(listOf(ConversionResponse(Currency.EUR, Currency.RON, date2, BigDecimal("5.0"))))
        whenever(instrumentConverterRegistry.getConverter(pricingInstrument))
            .thenReturn(instrumentConverter)
        whenever(instrumentConverter.convert(pricingInstrument, listOf(date2)))
            .thenReturn(
                listOf(
                    ConversionResponse(instrument, Currency.EUR, date2, BigDecimal("101.0"))
                )
            )

        val request = ConversionsRequest(
            listOf(
                ConversionRequest(instrument, Currency.RON, date1),
                ConversionRequest(instrument, Currency.RON, date2)
            )
        )

        val response = conversionService.convert(request)

        assertThat(response.conversions).hasSize(2)
        assertThat(response.getRate(instrument, Currency.RON, date1)).isEqualTo(BigDecimal("502.5"))
        assertThat(response.getRate(instrument, Currency.RON, date2)).isEqualTo(BigDecimal("505.00"))
    }
}
