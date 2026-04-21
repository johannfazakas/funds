package ro.jf.funds.fund.sdk

import com.benasher44.uuid.Uuid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import mu.KotlinLogging.logger
import ro.jf.funds.platform.jvm.observability.tracing.withSuspendingSpan
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import ro.jf.funds.platform.jvm.web.createHttpClient
import ro.jf.funds.platform.jvm.web.toApiException
import ro.jf.funds.fund.api.CategoryApi
import ro.jf.funds.fund.api.model.CreateCategoryTO
import ro.jf.funds.fund.api.model.CategoryTO

private val log = logger { }

class CategorySdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : CategoryApi {
    override suspend fun listCategories(userId: Uuid): List<CategoryTO> = withSuspendingSpan {
        val response = httpClient.get("$baseUrl$BASE_PATH/categories") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.OK) {
            log.warn { "Unexpected response on list categories: $response" }
            throw response.toApiException()
        }
        response.body()
    }

    override suspend fun createCategory(userId: Uuid, request: CreateCategoryTO): CategoryTO = withSuspendingSpan {
        val response = httpClient.post("$baseUrl$BASE_PATH/categories") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status != HttpStatusCode.Created) {
            log.warn { "Unexpected response on create category: $response" }
            throw response.toApiException()
        }
        response.body()
    }

    override suspend fun deleteCategoryById(userId: Uuid, categoryId: Uuid) = withSuspendingSpan {
        val response = httpClient.delete("$baseUrl$BASE_PATH/categories/$categoryId") {
            headers {
                append(USER_ID_HEADER, userId.toString())
            }
        }
        if (response.status != HttpStatusCode.NoContent) {
            log.warn { "Unexpected response on delete category: $response" }
            throw response.toApiException()
        }
    }
}
