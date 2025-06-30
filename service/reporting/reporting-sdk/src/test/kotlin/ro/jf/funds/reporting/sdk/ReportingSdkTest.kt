package ro.jf.funds.reporting.sdk

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
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
import ro.jf.funds.reporting.api.serializer.YearMonthSerializer
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
            fundId = fundId,
            dataConfiguration = ReportDataConfigurationTO(
                currency = RON,
                filter = RecordFilterTO(labels = labelsOf("need", "want")),
                groups = listOf(
                    ReportGroupTO(name = "need", filter = RecordFilterTO.byLabels("need")),
                    ReportGroupTO(name = "want", filter = RecordFilterTO.byLabels("want"))
                ),
                features = ReportDataFeaturesConfigurationTO(
                    net = NetReportFeatureTO(enabled = true, applyFilter = true),
                    valueReport = GenericReportFeatureTO(enabled = true),
                    groupedNet = GenericReportFeatureTO(enabled = true),
                    groupedBudget = GroupedBudgetReportFeatureTO(
                        enabled = true,
                        distributions = listOf(
                            GroupedBudgetReportFeatureTO.BudgetDistributionTO(
                                default = true,
                                from = null,
                                groups = listOf(
                                    GroupedBudgetReportFeatureTO.GroupBudgetPercentageTO(
                                        group = "need",
                                        percentage = 60
                                    ),
                                    GroupedBudgetReportFeatureTO.GroupBudgetPercentageTO(
                                        group = "want",
                                        percentage = 40
                                    )
                                )
                            ),
                            GroupedBudgetReportFeatureTO.BudgetDistributionTO(
                                default = false,
                                from = YearMonthTO(2020, 1),
                                groups = listOf(
                                    GroupedBudgetReportFeatureTO.GroupBudgetPercentageTO(
                                        group = "need",
                                        percentage = 70
                                    ),
                                    GroupedBudgetReportFeatureTO.GroupBudgetPercentageTO(
                                        group = "want",
                                        percentage = 30
                                    )
                                )
                            )
                        )
                    )
                )
            ),
        )
        mockServerClient.mockCreateReportViewTask(request, taskId, "IN_PROGRESS")

        val response = reportingSdk.createReportView(userId, request)

        assertThat(response.taskId).isEqualTo(taskId)
        assertThat(response.status).isEqualTo(ReportViewTaskStatus.IN_PROGRESS)
    }

    @Test
    fun `get report view task`(mockServerClient: MockServerClient): Unit = runBlocking {
        mockServerClient.mockGetReportViewTask(taskId, "IN_PROGRESS")

        val response = reportingSdk.getReportViewTask(userId, taskId)

        assertThat(response.taskId).isEqualTo(taskId)
        assertThat(response.status).isEqualTo(ReportViewTaskStatus.IN_PROGRESS)
    }

    @Test
    fun `get report view`(mockServerClient: MockServerClient): Unit = runBlocking {
        val expectedResponse = ReportViewTO(
            id = viewId,
            name = viewName,
            fundId = fundId,
            dataConfiguration = ReportDataConfigurationTO(
                currency = RON,
                filter = RecordFilterTO(labels = labelsOf("need", "want")),
                groups = listOf(
                    ReportGroupTO(name = "need", filter = RecordFilterTO.byLabels("need")),
                    ReportGroupTO(name = "want", filter = RecordFilterTO.byLabels("want"))
                ),
                features = ReportDataFeaturesConfigurationTO(
                    NetReportFeatureTO(enabled = true, applyFilter = true),
                    GenericReportFeatureTO(enabled = true)
                )
            )
        )
        mockServerClient.mockGetReportView(expectedResponse)

        val response = reportingSdk.getReportView(userId, fundId)

        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `list report views`(mockServerClient: MockServerClient): Unit = runBlocking {
        val expectedResponse = ListTO.of(
            ReportViewTO(
                id = viewId,
                name = viewName,
                fundId = fundId,
                dataConfiguration = ReportDataConfigurationTO(
                    currency = RON,
                    filter = RecordFilterTO(labels = labelsOf("need", "want")),
                    groups = listOf(
                        ReportGroupTO(name = "need", RecordFilterTO.byLabels("need")),
                        ReportGroupTO(name = "want", RecordFilterTO.byLabels("want"))
                    ),
                    features = ReportDataFeaturesConfigurationTO(
                        NetReportFeatureTO(enabled = true, applyFilter = true),
                        GenericReportFeatureTO(enabled = true)
                    )
                )
            )
        )
        mockServerClient.mockListReportViews(expectedResponse)

        val response = reportingSdk.listReportViews(userId)

        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `get report view data`(mockServerClient: MockServerClient): Unit = runBlocking {
        val expectedResponse = ReportDataTO(
            viewId = viewId,
            interval = ReportDataIntervalTO(
                granularity = TimeGranularityTO.MONTHLY,
                fromDate = LocalDate(2024, 11, 1),
                toDate = LocalDate(2025, 1, 31),
            ),
            data = listOf(
                ReportDataItemTO(
                    timeBucket = DateIntervalTO(YearMonthTO(2024, 11), YearMonthTO(2024, 11)),
                    bucketType = BucketTypeTO.REAL,
                    net = BigDecimal("200.0"),
                    value = ValueReportTO(
                        start = BigDecimal("210.0"),
                        end = BigDecimal("200.0"),
                        min = BigDecimal("150.0"),
                        max = BigDecimal("220.0")
                    ),
                    groupedNet = null,
                    groupedBudget = listOf(
                        ReportDataGroupedBudgetItemTO(
                            group = "need",
                            allocated = BigDecimal("150.0"),
                            spent = BigDecimal("100.0"),
                            left = BigDecimal("50.0")
                        ),
                        ReportDataGroupedBudgetItemTO(
                            group = "want",
                            allocated = BigDecimal("200.0"),
                            spent = BigDecimal("100.0"),
                            left = BigDecimal("100.0")
                        )
                    )
                ),
                ReportDataItemTO(
                    timeBucket = DateIntervalTO(YearMonthTO(2024, 12), YearMonthTO(2024, 12)),
                    bucketType = BucketTypeTO.REAL,
                    net = BigDecimal("300.0"),
                    value = ValueReportTO(
                        start = BigDecimal("310.0"),
                        end = BigDecimal("300.0"),
                        min = BigDecimal("250.0"),
                        max = BigDecimal("320.0")
                    ),
                    groupedNet = null,
                    groupedBudget = listOf(
                        ReportDataGroupedBudgetItemTO(
                            group = "need",
                            allocated = BigDecimal("200.0"),
                            spent = BigDecimal("150.0"),
                            left = BigDecimal("50.0")
                        ),
                        ReportDataGroupedBudgetItemTO(
                            group = "want",
                            allocated = BigDecimal("300.0"),
                            spent = BigDecimal("150.0"),
                            left = BigDecimal("150.0")
                        )
                    )
                ),
                ReportDataItemTO(
                    timeBucket = DateIntervalTO(YearMonthTO(2025, 1), YearMonthTO(2025, 1)),
                    bucketType = BucketTypeTO.REAL,
                    net = BigDecimal("400.0"),
                    value = ValueReportTO(
                        start = BigDecimal("410.0"),
                        end = BigDecimal("400.0"),
                        min = BigDecimal("350.0"),
                        max = BigDecimal("420.0")
                    ),
                    groupedNet = null,
                    groupedBudget = listOf(
                        ReportDataGroupedBudgetItemTO(
                            group = "need",
                            allocated = BigDecimal("300.0"),
                            spent = BigDecimal("200.0"),
                            left = BigDecimal("100.0")
                        ),
                        ReportDataGroupedBudgetItemTO(
                            group = "want",
                            allocated = BigDecimal("400.0"),
                            spent = BigDecimal("200.0"),
                            left = BigDecimal("200.0")
                        )
                    )
                )
            )
        )
        mockServerClient.mockGetReportData(expectedResponse)

        val response =
            reportingSdk.getMonthlyReportViewData(userId, viewId, YearMonthTO(2024, 11), YearMonthTO(2025, 1))

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
                                    put("fundId", buildJsonObject {
                                        put("type", JsonPrimitive("string"))
                                        put("value", JsonPrimitive(request.fundId.toString()))
                                    })
                                    put("dataConfiguration", buildJsonObject {
                                        put("type", JsonPrimitive("object"))
                                        put("properties", buildJsonObject {
                                            put("currency", JsonPrimitive("string"))
                                            put("filter", buildJsonObject {
                                                put("type", JsonPrimitive("object"))
                                                put("properties", buildJsonObject {
                                                    put("labels", buildJsonObject {
                                                        put("type", JsonPrimitive("array"))
                                                        put("items", buildJsonObject {
                                                            put("type", JsonPrimitive("string"))
                                                        })
                                                    })
                                                })
                                            })
                                            put("groups", buildJsonObject {
                                                put("type", JsonPrimitive("array"))
                                                put("items", buildJsonObject {
                                                    put("type", JsonPrimitive("object"))
                                                    put("properties", buildJsonObject {
                                                        put("name", buildJsonObject {
                                                            put("type", JsonPrimitive("string"))
                                                        })
                                                        put("filter", buildJsonObject {
                                                            put("type", JsonPrimitive("object"))
                                                            put("properties", buildJsonObject {
                                                                put("labels", buildJsonObject {
                                                                    put("type", JsonPrimitive("array"))
                                                                    put("items", buildJsonObject {
                                                                        put("type", JsonPrimitive("string"))
                                                                    })
                                                                })
                                                            })
                                                        })
                                                    })
                                                })
                                            })
                                            put("features", buildJsonObject {
                                                put("type", JsonPrimitive("object"))
                                                put("properties", buildJsonObject {
                                                    put("net", buildJsonObject {
                                                        put("type", JsonPrimitive("object"))
                                                        put("properties", buildJsonObject {
                                                            put("enabled", buildJsonObject {
                                                                put("type", JsonPrimitive("boolean"))
                                                                put(
                                                                    "value",
                                                                    JsonPrimitive(request.dataConfiguration.features.net.enabled)
                                                                )
                                                            })
                                                            put("applyFilter", buildJsonObject {
                                                                put("type", JsonPrimitive("boolean"))
                                                                put(
                                                                    "value",
                                                                    JsonPrimitive(request.dataConfiguration.features.net.applyFilter)
                                                                )
                                                            })
                                                        })
                                                    })
                                                    put("valueReport", buildJsonObject {
                                                        put("type", JsonPrimitive("object"))
                                                        put("properties", buildJsonObject {
                                                            put("enabled", buildJsonObject {
                                                                put("type", JsonPrimitive("boolean"))
                                                                put(
                                                                    "value",
                                                                    JsonPrimitive(request.dataConfiguration.features.valueReport.enabled)
                                                                )
                                                            })
                                                        })
                                                    })
                                                })
                                            })
                                        })
                                    })
                                }
                            )
                            put("required", buildJsonArray {
                                add(JsonPrimitive("name"))
                                add(JsonPrimitive("fundId"))
                                add(JsonPrimitive("dataConfiguration"))
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
            put("dataConfiguration", buildJsonObject {
                put("currency", JsonPrimitive(response.dataConfiguration.currency.value))
                put("filter", buildJsonObject {
                    put("labels", buildJsonArray {
                        response.dataConfiguration.filter.labels?.forEach { label ->
                            add(JsonPrimitive(label.value))
                        }
                    })
                })
                put("groups", buildJsonArray {
                    response.dataConfiguration.groups?.forEach { group ->
                        add(
                            buildJsonObject {
                                put("name", JsonPrimitive(group.name))
                                put("filter", buildJsonObject {
                                    put("labels", buildJsonArray {
                                        group.filter.labels?.forEach { label ->
                                            add(JsonPrimitive(label.value))
                                        }
                                    })
                                })
                            }
                        )
                    }
                })
                put("features", buildJsonObject {
                    put("net", buildJsonObject {
                        put("enabled", JsonPrimitive(response.dataConfiguration.features.net.enabled))
                        put("applyFilter", JsonPrimitive(response.dataConfiguration.features.net.applyFilter))
                    })
                    put("valueReport", buildJsonObject {
                        put("enabled", JsonPrimitive(response.dataConfiguration.features.valueReport.enabled))
                    })
                })
            })
        })

    private fun MockServerClient.mockGetReportData(expectedResponse: ReportDataTO) {
        `when`(
            request()
                .withMethod("GET")
                .withPath("/funds-api/reporting/v1/report-views/$viewId/data")
                .withQueryStringParameters(
                    mapOf(
                        "granularity" to listOf(expectedResponse.interval.granularity.name),
                        "fromYearMonth" to expectedResponse.interval.fromDate
                            .let { YearMonthTO(it.year, it.monthNumber) }
                            .let { it-> Json.encodeToString(YearMonthSerializer(), it) }
                            .let { it: String -> listOf(it) },
                        "toYearMonth" to expectedResponse.interval.toDate
                            .let { YearMonthTO(it.year, it.monthNumber) }
                            .let { it-> Json.encodeToString(YearMonthSerializer(), it) }
                            .let { it: String -> listOf(it) },
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
                            put("interval", buildJsonObject {
                                put("granularity", JsonPrimitive(expectedResponse.interval.granularity.name))
                                put(
                                    "fromDate",
                                    JsonPrimitive(expectedResponse.interval.fromDate.toString())
                                )
                                put("toDate", JsonPrimitive(expectedResponse.interval.toDate.toString()))
                                put(
                                    "forecastUntilDate",
                                    JsonPrimitive(expectedResponse.interval.forecastUntilDate?.toString())
                                )
                            })
                            put("data", buildJsonArray {
                                expectedResponse.data.forEach { item ->
                                    add(
                                        buildJsonObject {
                                            put("timeBucket", buildJsonObject {
                                                put("from", JsonPrimitive(item.timeBucket.from.toString()))
                                                put("to", JsonPrimitive(item.timeBucket.to.toString()))
                                            })
                                            put("bucketType", JsonPrimitive(item.bucketType.name))
                                            put("net", JsonPrimitive(item.net.toString()))
                                            put("value", buildJsonObject {
                                                put("start", JsonPrimitive(item.value?.start.toString()))
                                                put("end", JsonPrimitive(item.value?.end.toString()))
                                                put("min", JsonPrimitive(item.value?.min.toString()))
                                                put("max", JsonPrimitive(item.value?.max.toString()))
                                            })
                                            put("groupedBudget", buildJsonArray {
                                                item.groupedBudget?.forEach { group ->
                                                    add(
                                                        buildJsonObject {
                                                            put("group", JsonPrimitive(group.group))
                                                            put("spent", JsonPrimitive(group.spent.toString()))
                                                            put("allocated", JsonPrimitive(group.allocated.toString()))
                                                            put("left", JsonPrimitive(group.left.toString()))
                                                        }
                                                    )
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
}
