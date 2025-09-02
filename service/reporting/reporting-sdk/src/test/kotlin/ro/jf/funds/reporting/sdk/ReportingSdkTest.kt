package ro.jf.funds.reporting.sdk

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
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
import java.util.UUID.randomUUID

@ExtendWith(MockServerContainerExtension::class)
class ReportingSdkTest {
    private val reportingSdk = ReportingSdk(baseUrl = MockServerContainerExtension.baseUrl)

    private val userId = randomUUID()
    private val viewId = randomUUID()
    private val fundId = randomUUID()
    private val viewName = "Expense Report"

    @Test
    fun `create report view`(mockServerClient: MockServerClient): Unit = runTest {
        val request = CreateReportViewTO(
            name = viewName,
            fundId = fundId,
            dataConfiguration = ReportDataConfigurationTO(
                currency = RON,
                groups = listOf(
                    ReportGroupTO(name = "need", filter = RecordFilterTO.byLabels("need")),
                    ReportGroupTO(name = "want", filter = RecordFilterTO.byLabels("want"))
                ),
                reports = ReportsConfigurationTO(
                    net = NetReportConfigurationTO(enabled = true, RecordFilterTO(labels = labelsOf("need", "want"))),
                    valueReport = ValueReportConfigurationTO(
                        enabled = true,
                        RecordFilterTO(labels = labelsOf("need", "want"))
                    ),
                    groupedNet = GenericReportConfigurationTO(enabled = true),
                    groupedBudget = GroupedBudgetReportConfigurationTO(
                        enabled = true,
                        distributions = listOf(
                            GroupedBudgetReportConfigurationTO.BudgetDistributionTO(
                                default = true,
                                from = null,
                                groups = listOf(
                                    GroupedBudgetReportConfigurationTO.GroupBudgetPercentageTO(
                                        group = "need",
                                        percentage = 60
                                    ),
                                    GroupedBudgetReportConfigurationTO.GroupBudgetPercentageTO(
                                        group = "want",
                                        percentage = 40
                                    )
                                )
                            ),
                            GroupedBudgetReportConfigurationTO.BudgetDistributionTO(
                                default = false,
                                from = YearMonthTO(2020, 1),
                                groups = listOf(
                                    GroupedBudgetReportConfigurationTO.GroupBudgetPercentageTO(
                                        group = "need",
                                        percentage = 70
                                    ),
                                    GroupedBudgetReportConfigurationTO.GroupBudgetPercentageTO(
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
        val expectedResponse = ReportViewTO(
            id = viewId,
            name = viewName,
            fundId = fundId,
            dataConfiguration = ReportDataConfigurationTO(
                currency = RON,
                groups = listOf(
                    ReportGroupTO(name = "need", filter = RecordFilterTO.byLabels("need")),
                    ReportGroupTO(name = "want", filter = RecordFilterTO.byLabels("want"))
                ),
                reports = ReportsConfigurationTO(
                    NetReportConfigurationTO(enabled = true, RecordFilterTO(labels = labelsOf("need", "want"))),
                    ValueReportConfigurationTO(enabled = true, RecordFilterTO(labels = labelsOf("need", "want")))
                )
            )
        )
        mockServerClient.mockCreateReportView(request, expectedResponse)

        val response = reportingSdk.createReportView(userId, request)

        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `get report view`(mockServerClient: MockServerClient): Unit = runBlocking {
        val expectedResponse = ReportViewTO(
            id = viewId,
            name = viewName,
            fundId = fundId,
            dataConfiguration = ReportDataConfigurationTO(
                currency = RON,
                groups = listOf(
                    ReportGroupTO(name = "need", filter = RecordFilterTO.byLabels("need")),
                    ReportGroupTO(name = "want", filter = RecordFilterTO.byLabels("want"))
                ),
                reports = ReportsConfigurationTO(
                    NetReportConfigurationTO(enabled = true, RecordFilterTO(labels = labelsOf("need", "want"))),
                    ValueReportConfigurationTO(enabled = true, RecordFilterTO(labels = labelsOf("need", "want")))
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
                    groups = listOf(
                        ReportGroupTO(name = "need", RecordFilterTO.byLabels("need")),
                        ReportGroupTO(name = "want", RecordFilterTO.byLabels("want"))
                    ),
                    reports = ReportsConfigurationTO(
                        NetReportConfigurationTO(enabled = true, RecordFilterTO(labels = labelsOf("need", "want"))),
                        ValueReportConfigurationTO(enabled = true, RecordFilterTO(labels = labelsOf("need", "want")))
                    )
                )
            )
        )
        mockServerClient.mockListReportViews(expectedResponse)

        val response = reportingSdk.listReportViews(userId)

        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `get net data`(mockServerClient: MockServerClient): Unit = runBlocking {
        val expectedResponse = ReportDataTO(
            viewId = viewId,
            interval = ReportDataIntervalTO.Monthly(
                fromYearMonth = YearMonthTO(2024, 11),
                toYearMonth = YearMonthTO(2025, 1),
            ),
            timeBuckets = listOf(
                BucketDataTO(
                    timeBucket = DateIntervalTO(YearMonthTO(2024, 11), YearMonthTO(2024, 11)),
                    bucketType = BucketTypeTO.REAL,
                    report = NetReportTO(BigDecimal("200.0"))
                ),
                BucketDataTO(
                    timeBucket = DateIntervalTO(YearMonthTO(2024, 12), YearMonthTO(2024, 12)),
                    bucketType = BucketTypeTO.REAL,
                    report = NetReportTO(BigDecimal("300.0"))
                ),
                BucketDataTO(
                    timeBucket = DateIntervalTO(YearMonthTO(2025, 1), YearMonthTO(2025, 1)),
                    bucketType = BucketTypeTO.REAL,
                    report = NetReportTO(BigDecimal("400.0"))
                )
            )
        )

        mockServerClient.mockGetReportData(
            "/funds-api/reporting/v1/report-views/$viewId/data/net",
            expectedResponse,
            ::buildNetItemJsonObject
        )

        val response =
            reportingSdk.getNetData(
                userId,
                viewId,
                ReportDataIntervalTO.Monthly(YearMonthTO(2024, 11), YearMonthTO(2025, 1))
            )

        assertThat(response).isEqualTo(expectedResponse)
    }

    private fun MockServerClient.mockCreateReportView(request: CreateReportViewTO, reportView: ReportViewTO) {
        `when`(
            request()
                .withMethod("POST")
                .withPath("/funds-api/reporting/v1/report-views")
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
                                                                    JsonPrimitive(request.dataConfiguration.reports.net.enabled)
                                                                )
                                                            })
                                                            request.dataConfiguration.reports.net.filter?.let { filter ->
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
                                                            }
                                                        })
                                                    })
                                                    put("valueReport", buildJsonObject {
                                                        put("type", JsonPrimitive("object"))
                                                        put("properties", buildJsonObject {
                                                            put("enabled", buildJsonObject {
                                                                put("type", JsonPrimitive("boolean"))
                                                                put(
                                                                    "value",
                                                                    JsonPrimitive(request.dataConfiguration.reports.valueReport.enabled)
                                                                )
                                                            })
                                                            request.dataConfiguration.reports.valueReport.filter?.let { filter ->
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
                                                            }
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
                    .withStatusCode(HttpStatusCode.Created.value)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        reportViewJsonObject(reportView).toString()
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
                put("reports", buildJsonObject {
                    put("net", buildJsonObject {
                        put("enabled", JsonPrimitive(response.dataConfiguration.reports.net.enabled))
                        put("filter", buildJsonObject {
                            put("labels", buildJsonArray {
                                response.dataConfiguration.reports.net.filter?.labels?.forEach { label ->
                                    add(JsonPrimitive(label.value))
                                }
                            })
                        })
                    })
                    put("valueReport", buildJsonObject {
                        put("enabled", JsonPrimitive(response.dataConfiguration.reports.valueReport.enabled))
                        put("filter", buildJsonObject {
                            put("labels", buildJsonArray {
                                response.dataConfiguration.reports.valueReport.filter?.labels?.forEach { label ->
                                    add(JsonPrimitive(label.value))
                                }
                            })
                        })
                    })
                })
            })
        })

    private fun <T> MockServerClient.mockGetReportData(
        path: String,
        response: ReportDataTO<T>,
        itemDataMapper: (T) -> JsonObject,
    ) {
        `when`(
            request()
                .withMethod("GET")
                .withPath(path)
                .withQueryStringParameters(
                    expectedDateIntervalQueryParameters(response.interval)
                )
                .withHeader(USER_ID_HEADER, userId.toString())
        )
            .respond(
                response()
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("viewId", JsonPrimitive(response.viewId.toString()))
                            put("interval", buildDataIntervalJson(response.interval))
                            put("timeBuckets", buildJsonArray {
                                response.timeBuckets.forEach { item ->
                                    add(
                                        buildJsonObject {
                                            put("timeBucket", buildJsonObject {
                                                put("from", JsonPrimitive(item.timeBucket.from.toString()))
                                                put("to", JsonPrimitive(item.timeBucket.to.toString()))
                                            })
                                            put("bucketType", JsonPrimitive(item.bucketType.name))
                                            put("report", itemDataMapper(item.report))
                                        }
                                    )
                                }
                            })
                        }.toString()
                    )
            )
    }

    private fun buildNetItemJsonObject(itemData: NetReportTO): JsonObject =
        buildJsonObject {
            put("net", JsonPrimitive(itemData.net.toString()))
        }

    private fun expectedDateIntervalQueryParameters(interval: ReportDataIntervalTO): Map<String, List<String>> {
        return when (interval) {
            is ReportDataIntervalTO.Daily -> {
                mapOf(
                    "granularity" to interval.granularity.name,
                    "fromDate" to interval.fromDate.toString(),
                    "toDate" to interval.toDate.toString(),
                    "forecastUntilDate" to interval.forecastUntilDate?.toString(),
                )
            }

            is ReportDataIntervalTO.Monthly -> {
                mapOf(
                    "granularity" to interval.granularity.name,
                    "fromYearMonth" to interval.fromYearMonth.toString(),
                    "toYearMonth" to interval.toYearMonth.toString(),
                    "forecastUntilYearMonth" to interval.forecastUntilYearMonth?.toString(),
                )
            }

            is ReportDataIntervalTO.Yearly -> {
                mapOf(
                    "granularity" to interval.granularity.name,
                    "fromYear" to interval.fromYear.toString(),
                    "toYear" to interval.toYear.toString(),
                    "forecastUntilYear" to interval.forecastUntilYear?.toString(),
                )
            }
        }
            .mapValues { listOfNotNull(it.value) }
            .filter { it.value.isNotEmpty() }
    }

    private fun buildDataIntervalJson(
        interval: ReportDataIntervalTO,
    ): JsonObject = buildJsonObject {
        put("granularity", JsonPrimitive(interval.granularity.name))
        when (interval) {
            is ReportDataIntervalTO.Yearly -> {
                put("fromYear", JsonPrimitive(interval.fromYear.toString()))
                put("toYear", JsonPrimitive(interval.toYear.toString()))
                if (interval.forecastUntilYear != null) {
                    put("forecastUntilYear", JsonPrimitive(interval.forecastUntilYear.toString()))
                }
            }

            is ReportDataIntervalTO.Monthly -> {
                put(
                    "fromYearMonth",
                    Json.encodeToJsonElement(YearMonthSerializer(), interval.fromYearMonth)
                )
                put(
                    "toYearMonth",
                    Json.encodeToJsonElement(YearMonthSerializer(), interval.toYearMonth)
                )
                if (interval.forecastUntilYearMonth != null) {
                    put(
                        "forecastUntilYearMonth",
                        Json.encodeToJsonElement(YearMonthSerializer(), interval.forecastUntilYearMonth!!)
                    )
                }
            }

            is ReportDataIntervalTO.Daily -> {
                put(
                    "fromDate",
                    JsonPrimitive(interval.fromDate.toString())
                )
                put("toDate", JsonPrimitive(interval.toDate.toString()))
                put(
                    "forecastUntilDate",
                    JsonPrimitive(interval.forecastUntilDate?.toString())
                )
            }
        }
    }
}
