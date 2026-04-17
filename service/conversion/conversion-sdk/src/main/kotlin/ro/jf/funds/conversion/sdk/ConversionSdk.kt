package ro.jf.funds.conversion.sdk

import com.github.benmanes.caffeine.cache.Cache
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.conversion.api.model.ConversionRequest
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.platform.jvm.web.createHttpClient
import ro.jf.funds.platform.jvm.web.toApiException

private val log = logger { }

private const val LOCALHOST_BASE_URL = "http://localhost:5231"

class ConversionSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
    private val cache: Cache<ConversionRequest, BigDecimal>,
) {

    suspend fun convert(request: ConversionsRequest): ConversionsResponse = withSuspendingSpan {
        request.conversions.partition { it.sourceUnit == it.targetCurrency }
            .let { (identityRequests, nonIdentityRequests) ->
                resolveIdentity(identityRequests) + resolveNonIdentity(nonIdentityRequests)
            }
            .let(::ConversionsResponse)
    }

    private fun resolveIdentity(requests: List<ConversionRequest>): List<ConversionResponse> =
        requests.map { ConversionResponse(it.sourceUnit, it.targetCurrency, it.date, BigDecimal.ONE) }

    private suspend fun resolveNonIdentity(requests: List<ConversionRequest>): List<ConversionResponse> {
        if (requests.isEmpty()) return emptyList()
        val cacheHits = cache.getAllPresent(requests)
        val cacheMisses = requests.filterNot { it in cacheHits }
        return resolveCacheHits(cacheHits) + resolveCacheMisses(cacheMisses)
    }

    private fun resolveCacheHits(hits: Map<ConversionRequest, BigDecimal>): List<ConversionResponse> =
        hits.map { (req, rate) -> ConversionResponse(req.sourceUnit, req.targetCurrency, req.date, rate) }

    private suspend fun resolveCacheMisses(misses: List<ConversionRequest>): List<ConversionResponse> {
        if (misses.isEmpty()) return emptyList()
        val fetched = fetch(ConversionsRequest(misses))
        cache.putAll(fetched.conversions.associate { resp ->
            ConversionRequest(resp.sourceUnit, resp.targetCurrency, resp.date) to resp.rate
        })
        return fetched.conversions
    }

    private suspend fun fetch(request: ConversionsRequest): ConversionsResponse {
        val response = httpClient.post("${baseUrl}/funds-api/conversion/v1/conversions") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on conversion: $response" }
            throw response.toApiException()
        }
        return response.body()
    }
}
