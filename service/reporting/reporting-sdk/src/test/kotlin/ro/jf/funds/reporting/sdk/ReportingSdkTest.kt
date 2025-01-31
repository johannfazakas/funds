package ro.jf.funds.reporting.sdk

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
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
import ro.jf.funds.reporting.api.model.*
import java.math.BigDecimal
import java.util.*
import java.util.UUID.randomUUID

import ro.jf.funds.commons.model.labelsOf

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
            fundId = fundId,
            labels = labelsOf("need", "want")
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
        val expectedResponse = ReportViewTO(viewId, viewName, fundId, ReportViewType.EXPENSE, labelsOf("need", "want"))
        mockServerClient.mockGetReportView(expectedResponse)

        val response = reportingSdk.getReportView(userId, fundId)

        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `list report views`(mockServerClient: MockServerClient): Unit = runBlocking {
        val expectedResponse = ListTO.of(
            ReportViewTO(viewId, viewName, fundId, ReportViewType.EXPENSE, labelsOf("need", "want"))
        )
        mockServerClient.mockListReportViews(expectedResponse)

        val response = reportingSdk.listReportViews(userId)

        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `get report view data`(mockServerClient: MockServerClient): Unit = runBlocking {
        val granularInterval = GranularDateInterval(
            interval = DateInterval(LocalDate.parse("2024-11-01"), LocalDate.parse("2025-01-31")),
            granularity = TimeGranularity.MONTHLY
        )
        val expectedResponse = ExpenseReportDataTO(
            viewId = viewId,
            granularInterval = granularInterval,
            data = listOf(
                ExpenseReportDataTO.DataItem(
                    timeBucket = LocalDate.parse("2024-11-01"),
                    amount = BigDecimal("200.0")
                ),
                ExpenseReportDataTO.DataItem(
                    timeBucket = LocalDate.parse("2024-12-01"),
                    amount = BigDecimal("300.0")
                ),
                ExpenseReportDataTO.DataItem(
                    timeBucket = LocalDate.parse("2025-01-01"),
                    amount = BigDecimal("400.0")
                )
            )
        )
        mockServerClient.mockGetReportData(expectedResponse)

        val response = reportingSdk.getReportViewData(userId, viewId, granularInterval)

        assertThat(response).isEqualTo(expectedResponse)
    }

    private fun MockServerClient.mockCreateReportViewTask(request: CreateReportViewTO, taskId: UUID, status: String) {
        `when`(
            request()
                .withMethod("POST")
                .withPath("/funds-api/reporting/v1/report-views/tasks")
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
                .withPath("/funds-api/reporting/v1/report-views/tasks/$taskId")
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
                .withPath("/funds-api/reporting/v1/report-views/$fundId")
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
                            put("labels", buildJsonArray {
                                response.labels.forEach { label ->
                                    add(JsonPrimitive(label.value))
                                }
                            })
                        }.toString()
                    )
            )
    }

    private fun MockServerClient.mockListReportViews(response: ListTO<ReportViewTO>) {
        `when`(
            request()
                .withMethod("GET")
                .withPath("/funds-api/reporting/v1/report-views")
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
                                            put("labels", buildJsonArray {
                                                item.labels.forEach { label ->
                                                    add(JsonPrimitive(label.value))
                                                }
                                            })
                                        }
                                    )
                                }
                            })
                        }.toString()
                    )
            )
    }

    private fun MockServerClient.mockGetReportData(expectedResponse: ReportDataTO) {
        `when`(
            request()
                .withMethod("GET")
                .withPath("/funds-api/reporting/v1/report-views/$viewId/data")
                .withQueryStringParameters(
                    mapOf(
                        "from" to listOf(expectedResponse.granularInterval.interval.from.toString()),
                        "to" to listOf(expectedResponse.granularInterval.interval.to.toString()),
                        "granularity" to listOf(expectedResponse.granularInterval.granularity.name)
                    )
                )
                .withHeader(USER_ID_HEADER, userId.toString())
        )
            .respond(
                response()
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("viewId", JsonPrimitive(expectedResponse.viewId.toString()))
                            put(
                                "type", JsonPrimitive(
                                    when (expectedResponse) {
                                        is ExpenseReportDataTO -> "EXPENSE"
                                    }
                                )
                            )
                            put("granularInterval", buildJsonObject {
                                put("interval", buildJsonObject {
                                    put(
                                        "from",
                                        JsonPrimitive(expectedResponse.granularInterval.interval.from.toString())
                                    )
                                    put("to", JsonPrimitive(expectedResponse.granularInterval.interval.to.toString()))
                                })
                                put("granularity", JsonPrimitive(expectedResponse.granularInterval.granularity.name))
                            })
                            put("data", buildJsonArray {
                                expectedResponse.data.forEach { item ->
                                    add(
                                        buildJsonObject {
                                            put("timeBucket", JsonPrimitive(item.timeBucket.toString()))
                                            put("amount", JsonPrimitive(item.amount.toString()))
                                        }
                                    )
                                }
                            })
                        }.toString()
                    )
            )
    }
}

