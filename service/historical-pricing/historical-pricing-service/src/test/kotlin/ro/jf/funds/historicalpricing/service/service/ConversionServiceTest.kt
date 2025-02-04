package ro.jf.funds.historicalpricing.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyConverter
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyPairHistoricalPriceRepository
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyService

class ConversionServiceTest {
    private val currencyConverter = mock<CurrencyConverter>()
    private val historicalPriceRepository = mock<CurrencyPairHistoricalPriceRepository>()

    private val conversionService = ConversionService(CurrencyService(currencyConverter, historicalPriceRepository))

    @Test
    fun `should return saved conversions when available`(): Unit = runBlocking {
        val request = ConversionsRequest(
            listOf(
                ConversionRequest(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-04")),
                ConversionRequest(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-02")),
                ConversionRequest(Currency.EUR, Currency.RON, LocalDate.parse("2025-02-03"))
            )
        )

        val response = conversionService.convert(request)
    }

    @Test
    fun `should return and save new conversions when not available in storage`() {

    }
}
