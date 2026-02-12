package ro.jf.funds.client.sdk

import co.touchlab.kermit.Logger
import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import ro.jf.funds.fund.api.model.RecordFilterTO
import ro.jf.funds.fund.api.model.RecordSortField
import ro.jf.funds.fund.api.model.TransactionRecordTO
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.PageTO
import ro.jf.funds.platform.api.model.SortRequest

private const val LOCALHOST_BASE_URL = "http://localhost:5253"
private const val BASE_PATH = "/funds-api/fund/v1"

class RecordClient(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val log = Logger.withTag("RecordClient")

    suspend fun listRecords(
        userId: Uuid,
        filter: RecordFilterTO? = null,
        pageRequest: PageRequest? = null,
        sortRequest: SortRequest<RecordSortField>? = null,
    ): PageTO<TransactionRecordTO> {
        val response = httpClient.get("$baseUrl$BASE_PATH/records") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            url {
                filter?.accountId?.let { parameters.append("accountId", it.toString()) }
                filter?.fundId?.let { parameters.append("fundId", it.toString()) }
                filter?.unit?.let { parameters.append("unit", it) }
                filter?.label?.let { parameters.append("label", it) }
                parameters.appendPageRequest(pageRequest)
                parameters.appendSortRequest(sortRequest)
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.w { "Unexpected response on list records: $response" }
            throw Exception("Failed to list records: ${response.status}")
        }
        val records = response.body<PageTO<TransactionRecordTO>>()
        log.d { "Retrieved records: $records" }
        return records
    }
}
