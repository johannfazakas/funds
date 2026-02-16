package ro.jf.funds.fund.sdk

import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.fund.api.RecordApi
import ro.jf.funds.fund.api.model.RecordFilterTO
import ro.jf.funds.fund.api.model.RecordSortField
import ro.jf.funds.fund.api.model.RecordTO
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.PageTO
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.platform.jvm.client.appendPageRequest
import ro.jf.funds.platform.jvm.client.appendSortRequest
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import ro.jf.funds.platform.jvm.web.createHttpClient
import ro.jf.funds.platform.jvm.web.toApiException

private val log = logger { }

class RecordSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : RecordApi {
    override suspend fun listRecords(
        userId: Uuid,
        filter: RecordFilterTO?,
        pageRequest: PageRequest?,
        sortRequest: SortRequest<RecordSortField>?,
    ): PageTO<RecordTO> = withSuspendingSpan {
        val response = httpClient.get("$baseUrl$BASE_PATH/records") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            url {
                filter?.accountId?.let { parameters.append("accountId", it.toString()) }
                filter?.fundId?.let { parameters.append("fundId", it.toString()) }
                filter?.unit?.let { parameters.append("unit", it) }
                filter?.label?.let { parameters.append("label", it) }
                filter?.fromDate?.let { parameters.append("fromDate", it.toString()) }
                filter?.toDate?.let { parameters.append("toDate", it.toString()) }
                parameters.appendPageRequest(pageRequest)
                parameters.appendSortRequest(sortRequest)
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list records: $response" }
            throw response.toApiException()
        }
        val records = response.body<PageTO<RecordTO>>()
        log.debug { "Retrieved records: $records" }
        records
    }
}
