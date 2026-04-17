package ro.jf.funds.conversion.sdk

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.client.MockServerClient
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.MediaType
import org.mockserver.verify.VerificationTimes
import ro.jf.funds.conversion.api.model.ConversionRequest
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.platform.jvm.test.extension.MockServerContainerExtension
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

@ExtendWith(MockServerContainerExtension::class)
class ConversionSdkTest {

    private fun newSdk(ticker: Ticker = Ticker.systemTicker()): ConversionSdk =
        ConversionSdk(
            baseUrl = MockServerContainerExtension.baseUrl,
            cache = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(java.time.Duration.ofHours(24))
                .ticker(ticker)
                .build(),
        )

    @Test
    fun `should convert currencies`(mockServerClient: MockServerClient): Unit = runBlocking {
        val sdk = newSdk()
        val request = ConversionsRequest(
            listOf(
                ConversionRequest(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-01")),
                ConversionRequest(Currency.RON, Currency.EUR, LocalDate.parse("2025-01-31")),
                ConversionRequest(Currency.EUR, Currency.RON, LocalDate.parse("2025-01-28"))
            )
        )
        mockServerClient.mockConversionRequest(
            request.conversions[0] to BigDecimal.parseString("4.9"),
            request.conversions[1] to BigDecimal.parseString("4.8"),
            request.conversions[2] to BigDecimal.parseString("0.2")
        )

        val response = sdk.convert(request)

        assertThat(response.getRate(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-01")))
            .isEqualTo(BigDecimal.parseString("4.9"))
        assertThat(response.getRate(Currency.RON, Currency.EUR, LocalDate.parse("2025-01-31")))
            .isEqualTo(BigDecimal.parseString("4.8"))
        assertThat(response.getRate(Currency.EUR, Currency.RON, LocalDate.parse("2025-01-28")))
            .isEqualTo(BigDecimal.parseString("0.2"))
    }

    @Test
    fun `given cached entries - when convert called with same requests - then no http call is made`(
        mockServerClient: MockServerClient,
    ): Unit = runBlocking {
        val sdk = newSdk()
        val request = ConversionsRequest(
            listOf(ConversionRequest(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-01")))
        )
        mockServerClient.mockConversionRequest(
            request.conversions[0] to BigDecimal.parseString("4.9"),
            times = Times.once(),
        )

        sdk.convert(request)
        val response = sdk.convert(request)

        assertThat(response.getRate(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-01")))
            .isEqualTo(BigDecimal.parseString("4.9"))
        mockServerClient.verifyConversionCalls(VerificationTimes.exactly(1))
    }

    @Test
    fun `given partially cached request - when convert called - then only misses are forwarded over http`(
        mockServerClient: MockServerClient,
    ): Unit = runBlocking {
        val sdk = newSdk()
        val first = ConversionRequest(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-01"))
        val second = ConversionRequest(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-02"))
        mockServerClient.mockConversionRequest(first to BigDecimal.parseString("4.9"))

        sdk.convert(ConversionsRequest(listOf(first)))

        mockServerClient.reset()
        mockServerClient.mockConversionRequest(second to BigDecimal.parseString("5.0"))

        val response = sdk.convert(ConversionsRequest(listOf(first, second)))

        assertThat(response.getRate(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-01")))
            .isEqualTo(BigDecimal.parseString("4.9"))
        assertThat(response.getRate(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-02")))
            .isEqualTo(BigDecimal.parseString("5.0"))
        mockServerClient.verifyConversionPayloadOnly(second)
    }

    @Test
    fun `given empty incoming request - when convert called - then empty response returned and no http call`(
        mockServerClient: MockServerClient,
    ): Unit = runBlocking {
        val sdk = newSdk()

        val response = sdk.convert(ConversionsRequest(emptyList()))

        assertThat(response.conversions).isEmpty()
        mockServerClient.verifyConversionCalls(VerificationTimes.exactly(0))
    }

    @Test
    fun `given identity request - when convert called - then response contains rate one and no http call`(
        mockServerClient: MockServerClient,
    ): Unit = runBlocking {
        val sdk = newSdk()
        val identity = ConversionRequest(Currency.RON, Currency.RON, LocalDate.parse("2025-02-01"))

        val response = sdk.convert(ConversionsRequest(listOf(identity)))

        assertThat(response.getRate(Currency.RON, Currency.RON, LocalDate.parse("2025-02-01")))
            .isEqualTo(BigDecimal.ONE)
        mockServerClient.verifyConversionCalls(VerificationTimes.exactly(0))
    }

    @Test
    fun `given mix of identity and non-identity requests - when convert called - then http called only for non-identity`(
        mockServerClient: MockServerClient,
    ): Unit = runBlocking {
        val sdk = newSdk()
        val identity = ConversionRequest(Currency.EUR, Currency.EUR, LocalDate.parse("2025-02-01"))
        val nonIdentity = ConversionRequest(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-01"))
        mockServerClient.mockConversionRequest(nonIdentity to BigDecimal.parseString("0.2"))

        val response = sdk.convert(ConversionsRequest(listOf(identity, nonIdentity)))

        assertThat(response.getRate(Currency.EUR, Currency.EUR, LocalDate.parse("2025-02-01")))
            .isEqualTo(BigDecimal.ONE)
        assertThat(response.getRate(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-01")))
            .isEqualTo(BigDecimal.parseString("0.2"))
        mockServerClient.verifyConversionPayloadOnly(nonIdentity)
    }

    @Test
    fun `given cached entries past ttl - when convert called - then entries are re-fetched over http`(
        mockServerClient: MockServerClient,
    ): Unit = runBlocking {
        val ticker = FakeTicker()
        val sdk = newSdk(ticker)
        val request = ConversionsRequest(
            listOf(ConversionRequest(Currency.RON, Currency.EUR, LocalDate.parse("2025-02-01")))
        )
        mockServerClient.mockConversionRequest(
            request.conversions[0] to BigDecimal.parseString("4.9"),
        )

        sdk.convert(request)
        ticker.advance(Duration.ofHours(25))
        sdk.convert(request)

        mockServerClient.verifyConversionCalls(VerificationTimes.exactly(2))
    }

    private class FakeTicker : Ticker {
        private val nanos = AtomicLong(0)
        override fun read(): Long = nanos.get()
        fun advance(duration: Duration) {
            nanos.addAndGet(duration.toNanos())
        }
    }

    private fun MockServerClient.mockConversionRequest(
        vararg conversions: Pair<ConversionRequest, BigDecimal>,
        times: Times = Times.unlimited(),
    ) {
        `when`(
            request()
                .withMethod("POST")
                .withPath("/funds-api/conversion/v1/conversions"),
            times,
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
                                        put(
                                            "type", JsonPrimitive(
                                                when (conversion.sourceUnit) {
                                                    is Currency -> "currency"
                                                    is Instrument -> "instrument"
                                                }
                                            )
                                        )
                                        put("value", JsonPrimitive(conversion.sourceUnit.value))
                                    })
                                    put("targetCurrency", JsonPrimitive(conversion.targetCurrency.value))
                                    put("date", JsonPrimitive(conversion.date.toString()))
                                    put("rate", JsonPrimitive(rate.toString()))
                                })
                            }
                        })
                    }.toString()
                )
        )
    }

    private fun MockServerClient.verifyConversionCalls(times: VerificationTimes) {
        verify(
            request()
                .withMethod("POST")
                .withPath("/funds-api/conversion/v1/conversions"),
            times,
        )
    }

    private fun MockServerClient.verifyConversionPayloadOnly(vararg expected: ConversionRequest) {
        verify(
            request()
                .withMethod("POST")
                .withPath("/funds-api/conversion/v1/conversions"),
            VerificationTimes.exactly(1),
        )
        val received = retrieveRecordedRequests(
            request().withMethod("POST").withPath("/funds-api/conversion/v1/conversions")
        )
        val lastBody = received.last().bodyAsString
        expected.forEach { req ->
            assertThat(lastBody).contains(req.date.toString())
        }
        val entryCount = "\"sourceUnit\"".toRegex().findAll(lastBody).count()
        assertThat(entryCount).isEqualTo(expected.size)
    }
}
