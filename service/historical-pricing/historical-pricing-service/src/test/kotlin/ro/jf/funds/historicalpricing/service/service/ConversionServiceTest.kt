package ro.jf.funds.historicalpricing.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.service.domain.CurrencyPairHistoricalPrice
import ro.jf.funds.historicalpricing.service.domain.HistoricalPricingExceptions
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyConverter
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyPairHistoricalPriceRepository
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyService
import java.math.BigDecimal

class ConversionServiceTest {
    private val currencyConverter = mock<CurrencyConverter>()
    private val historicalPriceRepository = mock<CurrencyPairHistoricalPriceRepository>()

    private val conversionService = ConversionService(CurrencyService(currencyConverter, historicalPriceRepository))

    private val date1 = LocalDate.parse("2025-02-01")
    private val date2 = LocalDate.parse("2025-02-02")
    private val date3 = LocalDate.parse("2025-02-03")

    @Test
    fun `should return saved conversions when available`(): Unit = runBlocking {
        whenever(historicalPriceRepository.getHistoricalPrices(Currency.RON, Currency.EUR, listOf(date1, date2)))
            .thenReturn(
                listOf(
                    CurrencyPairHistoricalPrice(Currency.RON, Currency.EUR, date1, BigDecimal("0.2")),
                    CurrencyPairHistoricalPrice(Currency.RON, Currency.EUR, date2, BigDecimal("0.21"))
                )
            )
        whenever(historicalPriceRepository.getHistoricalPrices(Currency.EUR, Currency.RON, listOf(date3)))
            .thenReturn(
                listOf(
                    CurrencyPairHistoricalPrice(Currency.EUR, Currency.RON, date3, BigDecimal("4.9"))
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
        assertThat(response.getRate(Currency.RON, Currency.EUR, date3)).isNull()
    }

    @Test
    fun `should return and save new conversions when not available in storage`(): Unit = runBlocking {
        whenever(historicalPriceRepository.getHistoricalPrices(Currency.RON, Currency.EUR, listOf(date1, date2)))
            .thenReturn(
                listOf(
                    CurrencyPairHistoricalPrice(Currency.RON, Currency.EUR, date1, BigDecimal("0.2")),
                )
            )
        whenever(historicalPriceRepository.getHistoricalPrices(Currency.EUR, Currency.RON, listOf(date3)))
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
        assertThat(response.getRate(Currency.RON, Currency.EUR, date3)).isNull()
    }

    @Test
    fun `should raise an exception when attempting to convert investment symbols`(): Unit = runBlocking {
        val request = ConversionsRequest(listOf(ConversionRequest(Symbol("SXR8_DE"), Currency.EUR, date1)))

        assertThatThrownBy { runBlocking { conversionService.convert(request) } }
            .isInstanceOf(HistoricalPricingExceptions.ConversionNotPermitted::class.java)
    }
}
