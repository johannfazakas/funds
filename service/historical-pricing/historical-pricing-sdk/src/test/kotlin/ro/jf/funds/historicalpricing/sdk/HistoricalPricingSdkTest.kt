package ro.jf.funds.historicalpricing.sdk

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.client.MockServerClient
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.MediaType
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Symbol
import ro.jf.funds.commons.test.extension.MockServerContainerExtension
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import java.math.BigDecimal
import java.util.UUID.randomUUID

@ExtendWith(MockServerContainerExtension::class)
class HistoricalPricingSdkTest {
    private val historicalPricingSdk = HistoricalPricingSdk(baseUrl = MockServerContainerExtension.baseUrl)

    private val userId = randomUUID()

    @Test
    fun `should convert currencies`(mockServerClient: MockServerClient): Unit = runBlocking {
        val request = ConversionsRequest(
            listOf(
                ConversionRequest(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-01")),
                ConversionRequest(Currency.RON, Currency.EUR, LocalDate.parse("2025-01-31")),
                ConversionRequest(Currency.EUR, Currency.RON, LocalDate.parse("2025-01-28"))
            )
        )
        mockServerClient.mockConversionRequest(
            request.conversions[0] to BigDecimal("4.9"),
            request.conversions[1] to BigDecimal("4.8"),
            request.conversions[2] to BigDecimal("0.2")
        )

        val response = historicalPricingSdk.convert(userId, request)

        assertThat(response.getRate(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-01")))
            .isEqualTo(BigDecimal("4.9"))
        assertThat(response.getRate(Currency.RON, Currency.EUR, LocalDate.parse("2025-01-31")))
            .isEqualTo(BigDecimal("4.8"))
        assertThat(response.getRate(Currency.EUR, Currency.RON, LocalDate.parse("2025-01-28")))
            .isEqualTo(BigDecimal("0.2"))
    }

    private fun MockServerClient.mockConversionRequest(vararg conversions: Pair<ConversionRequest, BigDecimal>) {
        `when`(
            request()
                .withMethod("POST")
                .withPath("/funds-api/historical-pricing/v1/conversions")
                .withHeader(Header(USER_ID_HEADER, userId.toString()))
        ).respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(
                    buildJsonObject {
                        put("conversions", buildJsonArray {
                            conversions.forEach { (conversion, rate) ->
                                add(buildJsonObject {
                                    put("sourceUnit", buildJsonObject {
                                        put("type", JsonPrimitive(when (conversion.sourceUnit) {
                                            is Currency -> "currency"
                                            is Symbol -> "symbol"
                                        }))
                                        put("value", JsonPrimitive(conversion.sourceUnit.value))
                                    })
                                    put("targetUnit", buildJsonObject {
                                        put("type", JsonPrimitive(when (conversion.targetUnit) {
                                            is Currency -> "currency"
                                            is Symbol -> "symbol"
                                        }))
                                        put("value", JsonPrimitive(conversion.targetUnit.value))
                                    })
                                    put("date", JsonPrimitive(conversion.date.toString()))
                                    put("rate", JsonPrimitive(rate.toString()))
                                })
                            }
                        })
                    }.toString()
                )
        )
    }
}