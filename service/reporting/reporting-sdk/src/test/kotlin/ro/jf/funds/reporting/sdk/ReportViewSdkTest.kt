package ro.jf.funds.reporting.sdk

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.MediaType
import ro.jf.funds.commons.test.extension.MockServerContainerExtension
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.CreateReportViewTaskTO
import ro.jf.funds.reporting.api.model.ReportViewTypeTO
import java.util.UUID.randomUUID

@ExtendWith(MockServerContainerExtension::class)
class ReportViewSdkTest {
    private val reportViewSdk = ReportViewSdk(baseUrl = MockServerContainerExtension.baseUrl)

    val userId = randomUUID()
    val taskId = randomUUID()
    val fundId = randomUUID()

    @Test
    fun `given create report view`(mockServerClient: MockServerClient): Unit = runBlocking {
        mockServerClient
            .`when`(
                request()
                    .withMethod("POST")
                    .withPath("/bk-api/reporting/v1/report-views")
                    .withHeader(USER_ID_HEADER, userId.toString())
            )
            .respond(
                response()
                    .withStatusCode(202)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("taskId", JsonPrimitive(taskId.toString()))
                            put("type", JsonPrimitive("in_progress"))
                        }.toString()
                    )
            )
        val request = CreateReportViewTO(
            name = "Expense Report",
            type = ReportViewTypeTO.EXPENSE,
            fundId = fundId
        )

        val response = reportViewSdk.createReportView(userId, request)

        assertThat(response.taskId).isEqualTo(taskId)
        assertThat(response).isInstanceOf(CreateReportViewTaskTO.InProgress::class.java)
    }
}