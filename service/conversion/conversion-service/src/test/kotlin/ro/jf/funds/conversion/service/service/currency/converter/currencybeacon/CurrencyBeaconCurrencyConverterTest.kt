package ro.jf.funds.conversion.service.service.currency.converter.currencybeacon

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.MediaType
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.jvm.test.extension.MockServerContainerExtension
import ro.jf.funds.conversion.service.domain.ConversionExceptions

@ExtendWith(MockServerContainerExtension::class)
class CurrencyBeaconCurrencyConverterTest {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    private val currencyBeaconApiKey = "test_api_key"
    private val currencyConverter = CurrencyBeaconCurrencyConverter(
        httpClient = httpClient,
        baseUrl = MockServerContainerExtension.baseUrl,
        apiKey = currencyBeaconApiKey
    )

    @Test
    fun `should convert EUR to RON for multiple dates`(mockServerClient: MockServerClient): Unit = runBlocking {
        val date1 = LocalDate.parse("2021-02-28")
        val date2 = LocalDate.parse("2021-03-01")
        val dates = listOf(date1, date2)

        mockServerClient.mockCurrencyBeaconRequest("EUR", "RON", "2021-02-28", BigDecimal.parseString("4.87281309"))
        mockServerClient.mockCurrencyBeaconRequest("EUR", "RON", "2021-03-01", BigDecimal.parseString("4.88123456"))

        val result = currencyConverter.convert(Currency.EUR, Currency.RON, dates)

        assertThat(result).hasSize(2)
        assertThat(result[0].date).isEqualTo(date1)
        assertThat(result[0].rate).isEqualTo(BigDecimal.parseString("4.87281309"))
        assertThat(result[0].sourceUnit).isEqualTo(Currency.EUR)
        assertThat(result[0].targetCurrency).isEqualTo(Currency.RON)

        assertThat(result[1].date).isEqualTo(date2)
        assertThat(result[1].rate).isEqualTo(BigDecimal.parseString("4.88123456"))
        assertThat(result[1].sourceUnit).isEqualTo(Currency.EUR)
        assertThat(result[1].targetCurrency).isEqualTo(Currency.RON)
    }

    @Test
    fun `should throw exception when API limits are exceeded`(mockServerClient: MockServerClient): Unit =
        runBlocking {
            val date = LocalDate.parse("2021-02-28")
            val dates = listOf(date)

            mockServerClient.mockCurrencyBeaconLimitsExceededRequest("EUR", "RON", "2021-02-28")

            assertThatThrownBy {
                runBlocking { currencyConverter.convert(Currency.EUR, Currency.RON, dates) }
            }
                .isInstanceOf(ConversionExceptions.ConversionIntegrationException::class.java)
                .hasFieldOrPropertyWithValue("api", "currency_beacon")
                .hasFieldOrPropertyWithValue("status", 429)
                .hasFieldOrPropertyWithValue(
                    "errorDetail",
                    "Request limits exceeded. Please upgrade your account. See https://currencybeacon.com/api-documentation for details or email the support team at support@currencybeacon.com"
                )
        }

    @Test
    fun `should throw exception when API key is invalid`(mockServerClient: MockServerClient): Unit =
        runBlocking {
            val date = LocalDate.parse("2021-02-28")
            val dates = listOf(date)

            mockServerClient.mockCurrencyBeaconInvalidApiKeyRequest("EUR", "RON", "2021-02-28")

            assertThatThrownBy {
                runBlocking { currencyConverter.convert(Currency.EUR, Currency.RON, dates) }
            }
                .isInstanceOf(ConversionExceptions.ConversionIntegrationException::class.java)
                .hasFieldOrPropertyWithValue("api", "currency_beacon")
                .hasFieldOrPropertyWithValue("status", 401)
                .hasFieldOrPropertyWithValue(
                    "errorDetail",
                    "Missing or invalid api credentials. See https://currencybeacon.com/api-documentation for details."
                )
        }

    @Test
    fun `should fallback to previous day when rate is null`(mockServerClient: MockServerClient): Unit = runBlocking {
        val date = LocalDate.parse("2021-02-28")
        val dates = listOf(date)

        mockServerClient.mockCurrencyBeaconNullRateRequest("EUR", "RON", "2021-02-28")
        mockServerClient.mockCurrencyBeaconNullRateRequest("EUR", "RON", "2021-02-27")
        mockServerClient.mockCurrencyBeaconRequest("EUR", "RON", "2021-02-26", BigDecimal.parseString("4.87123456"))

        val result = currencyConverter.convert(Currency.EUR, Currency.RON, dates)

        assertThat(result).hasSize(1)
        assertThat(result[0].date).isEqualTo(date) // Should return original date
        assertThat(result[0].rate).isEqualTo(BigDecimal.parseString("4.87123456")) // Rate from previous day
        assertThat(result[0].sourceUnit).isEqualTo(Currency.EUR)
        assertThat(result[0].targetCurrency).isEqualTo(Currency.RON)
    }

    @Test
    fun `should fallback to previous day when rates are empty`(mockServerClient: MockServerClient): Unit = runBlocking {
        val date = LocalDate.parse("2021-02-28")
        val dates = listOf(date)

        mockServerClient.mockCurrencyBeaconEmptyRatesRequest("EUR", "RON", "2021-02-28")
        mockServerClient.mockCurrencyBeaconEmptyRatesRequest("EUR", "RON", "2021-02-27")
        mockServerClient.mockCurrencyBeaconRequest("EUR", "RON", "2021-02-26", BigDecimal.parseString("4.87123456"))

        val result = currencyConverter.convert(Currency.EUR, Currency.RON, dates)

        assertThat(result).hasSize(1)
        assertThat(result[0].date).isEqualTo(date) // Should return original date
        assertThat(result[0].rate).isEqualTo(BigDecimal.parseString("4.87123456")) // Rate from previous day
        assertThat(result[0].sourceUnit).isEqualTo(Currency.EUR)
        assertThat(result[0].targetCurrency).isEqualTo(Currency.RON)
    }

    private fun MockServerClient.mockCurrencyBeaconRequest(
        base: String,
        symbols: String,
        date: String,
        rate: BigDecimal,
    ) = mockCurrencyBeaconResponse(base, symbols, date, 200) {
        """
        {
          "meta": {
            "code": 200,
            "disclaimer": "Usage subject to terms: https://currencybeacon.com/terms"
          },
          "response": {
            "date": "$date",
            "base": "$base",
            "rates": {
              "$symbols": $rate
            }
          },
          "date": "$date",
          "base": "$base",
          "rates": {
            "$symbols": $rate
          }
        }
        """.trimIndent()
    }

    private fun MockServerClient.mockCurrencyBeaconLimitsExceededRequest(
        base: String,
        symbols: String,
        date: String,
    ) = mockCurrencyBeaconResponse(base, symbols, date, 429) {
        """
        {
          "meta": {
            "code": 429,
            "error_type": "auth failed",
            "error_detail": "Request limits exceeded. Please upgrade your account. See https://currencybeacon.com/api-documentation for details or email the support team at support@currencybeacon.com"
          },
          "response": []
        }
        """.trimIndent()
    }

    private fun MockServerClient.mockCurrencyBeaconInvalidApiKeyRequest(
        base: String,
        symbols: String,
        date: String,
    ) = mockCurrencyBeaconResponse(base, symbols, date, 401) {
        """
        {
          "meta": {
            "code": 401,
            "error_type": "auth failed",
            "error_detail": "Missing or invalid api credentials. See https://currencybeacon.com/api-documentation for details."
          },
          "response": []
        }
        """.trimIndent()
    }

    private fun MockServerClient.mockCurrencyBeaconNullRateRequest(
        base: String,
        symbols: String,
        date: String,
    ) = mockCurrencyBeaconResponse(base, symbols, date, 200) {
        """
        {
          "meta": {
            "code": 200,
            "disclaimer": "Usage subject to terms: https://currencybeacon.com/terms"
          },
          "response": {
            "date": "$date",
            "base": "$base",
            "rates": {
              "$symbols": null
            }
          },
          "date": "$date",
          "base": "$base",
          "rates": {
            "$symbols": null
          }
        }
        """.trimIndent()
    }

    private fun MockServerClient.mockCurrencyBeaconEmptyRatesRequest(
        base: String,
        symbols: String,
        date: String,
    ) = mockCurrencyBeaconResponse(base, symbols, date, 200) {
        """
        {
          "meta": {
            "code": 200,
            "disclaimer": "Usage subject to terms: https://currencybeacon.com/terms"
          },
          "response": {
            "date": "$date",
            "base": "$base",
            "rates": []
          },
          "date": "$date",
          "base": "$base",
          "rates": []
        }
        """.trimIndent()
    }

    private fun MockServerClient.mockCurrencyBeaconResponse(
        base: String,
        symbols: String,
        date: String,
        statusCode: Int,
        bodyProvider: () -> String,
    ) {
        `when`(
            request()
                .withMethod("GET")
                .withPath("/v1/historical")
                .withQueryStringParameter("base", base)
                .withQueryStringParameter("symbols", symbols)
                .withQueryStringParameter("date", date)
                .withQueryStringParameter("api_key", currencyBeaconApiKey)
        ).respond(
            response()
                .withStatusCode(statusCode)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(bodyProvider())
        )
    }
}