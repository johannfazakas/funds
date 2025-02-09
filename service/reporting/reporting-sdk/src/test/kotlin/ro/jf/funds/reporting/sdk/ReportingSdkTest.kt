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
import ro.jf.funds.commons.model.Currency.Companion.RON
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.model.labelsOf
import ro.jf.funds.commons.test.extension.MockServerContainerExtension
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.reporting.api.model.*
import java.math.BigDecimal
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
            fundId = fundId,
            currency = RON,
            labels = labelsOf("need", "want"),
            features = listOf(
                ReportingFeatureTO.MinMaxTotalValue
            )
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
        val expectedResponse = ReportViewTO(
            viewId, viewName, fundId, ReportViewType.EXPENSE, RON,
            labelsOf("need", "want"), listOf(ReportingFeatureTO.MinMaxTotalValue)
        )
        mockServerClient.mockGetReportView(expectedResponse)

        val response = reportingSdk.getReportView(userId, fundId)

        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `list report views`(mockServerClient: MockServerClient): Unit = runBlocking {
        val expectedResponse = ListTO.of(
            ReportViewTO(
                viewId, viewName, fundId, ReportViewType.EXPENSE, RON,
                labelsOf("need", "want"), listOf(ReportingFeatureTO.MinMaxTotalValue)
            )
        )
        mockServerClient.mockListReportViews(expectedResponse)

        val response = reportingSdk.listReportViews(userId)

        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `get expense report view data`(mockServerClient: MockServerClient): Unit = runBlocking {
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
                    amount = BigDecimal("200.0"),
                    minValue = BigDecimal("150.0"),
                    maxValue = BigDecimal("220.0")
                ),
                ExpenseReportDataTO.DataItem(
                    timeBucket = LocalDate.parse("2024-12-01"),
                    amount = BigDecimal("300.0"),
                    minValue = BigDecimal("250.0"),
                    maxValue = BigDecimal("320.0")
                ),
                ExpenseReportDataTO.DataItem(
                    timeBucket = LocalDate.parse("2025-01-01"),
                    amount = BigDecimal("400.0"),
                    minValue = BigDecimal("350.0"),
                    maxValue = BigDecimal("420.0")
                )
            )
        )
        mockServerClient.mockGetReportData(expectedResponse)

        val response = reportingSdk.getReportViewData(userId, viewId, granularInterval)

        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `get report view data`(mockServerClient: MockServerClient): Unit = runBlocking {
        val granularInterval = GranularDateInterval(
            interval = DateInterval(LocalDate.parse("2024-11-01"), LocalDate.parse("2025-01-31")),
            granularity = TimeGranularity.MONTHLY
        )
        val expectedResponse = FeaturesReportDataTO(
            viewId = viewId,
            granularInterval = granularInterval,
            features = listOf(
                FeatureReportDataTO.MinMaxTotalValue(
                    listOf(
                        FeatureReportDataTO.MinMaxTotalValue.DataItem(
                            timeBucket = LocalDate.parse("2024-11-01"),
                            minValue = BigDecimal("100.0"),
                            maxValue = BigDecimal("200.0"),
                        ),
                        FeatureReportDataTO.MinMaxTotalValue.DataItem(
                            timeBucket = LocalDate.parse("2024-12-01"),
                            minValue = BigDecimal("200.0"),
                            maxValue = BigDecimal("350.0"),
                        ),
                        FeatureReportDataTO.MinMaxTotalValue.DataItem(
                            timeBucket = LocalDate.parse("2025-01-01"),
                            minValue = BigDecimal("300.0"),
                            maxValue = BigDecimal("420.0"),
                        )
                    )
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
                                    put("featues", buildJsonArray {
                                        add(buildJsonObject {
                                            put("type", JsonPrimitive("min_max_total_value"))
                                        })
                                    })
                                }
                            )
                            put("required", buildJsonArray {
                                add(JsonPrimitive("name"))
                                add(JsonPrimitive("type"))
                                add(JsonPrimitive("fundId"))
                                add(JsonPrimitive("features"))
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
                        reportViewJsonObject(response).toString()
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
                                        reportViewJsonObject(item)
                                    )
                                }
                            })
                        }.toString()
                    )
            )
    }

    private fun reportViewJsonObject(response: ReportViewTO) =
        buildJsonObject({
            put("id", JsonPrimitive(response.id.toString()))
            put("name", JsonPrimitive(response.name))
            put("fundId", JsonPrimitive(response.fundId.toString()))
            put("type", JsonPrimitive(response.type.name))
            put("currency", buildJsonObject {
                put("value", JsonPrimitive(response.currency.value))
            })
            put("labels", buildJsonArray {
                response.labels.forEach { label ->
                    add(JsonPrimitive(label.value))
                }
            })
            put("features", buildJsonArray {
                response.features.forEach { feature ->
                    when (feature) {
                        is ReportingFeatureTO.MinMaxTotalValue -> add(buildJsonObject {
                            put("type", JsonPrimitive("min_max_total_value"))
                        })
                    }
                }
            })
        })

    private fun MockServerClient.mockGetReportData(expectedResponse: ExpenseReportDataTO) {
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
                            put("type", JsonPrimitive("EXPENSE"))
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
                                            put("minValue", JsonPrimitive(item.minValue.toString()))
                                            put("maxValue", JsonPrimitive(item.maxValue.toString()))
                                        }
                                    )
                                }
                            })
                        }.toString()
                    )
            )
    }

    private fun MockServerClient.mockGetReportData(expectedResponse: FeaturesReportDataTO) {
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
                            put("type", JsonPrimitive("FEATURES"))
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
                            put("features", buildJsonArray {
                                expectedResponse.features.forEach { feature ->
                                    when (feature) {
                                        is FeatureReportDataTO.MinMaxTotalValue -> add(buildJsonObject {
                                            put("type", JsonPrimitive("min_max_total_value"))
                                            put("data", buildJsonArray {
                                                feature.data.forEach { item ->
                                                    add(
                                                        buildJsonObject {
                                                            put("timeBucket", JsonPrimitive(item.timeBucket.toString()))
                                                            put("minValue", JsonPrimitive(item.minValue.toString()))
                                                            put("maxValue", JsonPrimitive(item.maxValue.toString()))
                                                        }
                                                    )
                                                }
                                            })
                                        })
                                    }
                                }
                            })
                        }.toString()
                    )
            )
    }
}

