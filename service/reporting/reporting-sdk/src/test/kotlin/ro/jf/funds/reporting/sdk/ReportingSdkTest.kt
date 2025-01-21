package ro.jf.funds.reporting.sdk

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.JsonSchemaBody.jsonSchema
import org.mockserver.model.MediaType
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.test.extension.MockServerContainerExtension
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTaskTO
import ro.jf.funds.reporting.api.model.ReportViewType
import java.util.*
import java.util.UUID.randomUUID

@ExtendWith(MockServerContainerExtension::class)
class ReportingSdkTest {
    private val reportingSdk = ReportingSdk(baseUrl = MockServerContainerExtension.baseUrl)

    private val userId = randomUUID()
    private val taskId = randomUUID()
    private val viewId = randomUUID()
    private val fundId = randomUUID()
    private val viewName = "Expense Report"

    @Test
    fun `create report view task`(mockServerClient: MockServerClient): Unit = runBlocking {
        val request = CreateReportViewTO(
            name = viewName,
            type = ReportViewType.EXPENSE,
            fundId = fundId
        )
        mockServerClient.mockCreateReportViewTask(request, taskId, "IN_PROGRESS")

        val response = reportingSdk.createReportView(userId, request)

        assertThat(response.taskId).isEqualTo(taskId)
        assertThat(response).isInstanceOf(ReportViewTaskTO.InProgress::class.java)
    }

    @Test
    fun `get report view task`(mockServerClient: MockServerClient): Unit = runBlocking {
        mockServerClient.mockGetReportViewTask(taskId, "IN_PROGRESS")

        val response = reportingSdk.getReportViewTask(userId, taskId)

        assertThat(response.taskId).isEqualTo(taskId)
        assertThat(response).isInstanceOf(ReportViewTaskTO.InProgress::class.java)
    }

    @Test
    fun `get report view`(mockServerClient: MockServerClient): Unit = runBlocking {
        val expectedResponse = ReportViewTO(viewId, viewName, fundId, ReportViewType.EXPENSE)
        mockServerClient.mockGetReportView(expectedResponse)

        val response = reportingSdk.getReportView(userId, fundId)

        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `given get report view list`(mockServerClient: MockServerClient): Unit = runBlocking {
        val expectedResponse = ListTO.of(ReportViewTO(viewId, viewName, fundId, ReportViewType.EXPENSE))
        mockServerClient.mockListReportViews(expectedResponse)

        val response = reportingSdk.listReportsViews(userId)

        assertThat(response).isEqualTo(expectedResponse)
    }

    private fun MockServerClient.mockCreateReportViewTask(request: CreateReportViewTO, taskId: UUID, status: String) {
        `when`(
            request()
                .withMethod("POST")
                .withPath("/bk-api/reporting/v1/report-views/tasks")
                .withHeader(USER_ID_HEADER, userId.toString())
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(
                    jsonSchema(
                        buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put(
                                "properties", buildJsonObject {
                                    put("name", buildJsonObject {
                                        put("type", JsonPrimitive("string"))
                                        put("value", JsonPrimitive(request.name))
                                    })
                                    put("type", buildJsonObject {
                                        put("type", JsonPrimitive("string"))
                                        put("value", JsonPrimitive(request.type.name))
                                    })
                                    put("fundId", buildJsonObject {
                                        put("type", JsonPrimitive("string"))
                                        put("value", JsonPrimitive(request.fundId.toString()))
                                    })
                                }
                            )
                            put("required", buildJsonArray {
                                add(JsonPrimitive("name"))
                                add(JsonPrimitive("type"))
                                add(JsonPrimitive("fundId"))
                            })
                        }.toString()
                    )
                )
        )
            .respond(
                response()
                    .withStatusCode(202)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("taskId", JsonPrimitive(taskId.toString()))
                            put("status", JsonPrimitive(status))
                        }.toString()
                    )
            )
    }

    private fun MockServerClient.mockGetReportViewTask(taskId: UUID, status: String) {
        `when`(
            request()
                .withMethod("GET")
                .withPath("/bk-api/reporting/v1/report-views/tasks/$taskId")
                .withHeader(USER_ID_HEADER, userId.toString())
        )
            .respond(
                response()
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("taskId", JsonPrimitive(taskId.toString()))
                            put("status", JsonPrimitive(status))
                        }.toString()
                    )
            )
    }

    private fun MockServerClient.mockGetReportView(response: ReportViewTO) {
        `when`(
            request()
                .withMethod("GET")
                .withPath("/bk-api/reporting/v1/report-views/$fundId")
                .withHeader(USER_ID_HEADER, userId.toString())
        )
            .respond(
                response()
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("id", JsonPrimitive(response.id.toString()))
                            put("name", JsonPrimitive(response.name))
                            put("fundId", JsonPrimitive(response.fundId.toString()))
                            put("type", JsonPrimitive(response.type.name))
                        }.toString()
                    )
            )
    }

    private fun MockServerClient.mockListReportViews(response: ListTO<ReportViewTO>) {
        `when`(
            request()
                .withMethod("GET")
                .withPath("/bk-api/reporting/v1/report-views")
                .withHeader(USER_ID_HEADER, userId.toString())
        )
            .respond(
                response()
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("items", buildJsonArray {
                                response.items.forEach { item ->
                                    add(
                                        buildJsonObject {
                                            put("id", JsonPrimitive(item.id.toString()))
                                            put("name", JsonPrimitive(item.name))
                                            put("fundId", JsonPrimitive(item.fundId.toString()))
                                            put("type", JsonPrimitive(item.type.name))
                                        }
                                    )
                                }
                            })
                        }.toString()
                    )
            )
    }
}