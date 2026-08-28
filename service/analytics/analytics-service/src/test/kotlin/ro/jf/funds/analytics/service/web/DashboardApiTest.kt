package ro.jf.funds.analytics.service.web

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.mockito.kotlin.mock
import ro.jf.funds.analytics.api.model.CreateDashboardChartTO
import ro.jf.funds.analytics.api.model.CreateDashboardTO
import ro.jf.funds.analytics.api.model.DashboardChartTO
import ro.jf.funds.analytics.api.model.DashboardLookbackTO
import ro.jf.funds.analytics.api.model.DashboardLookbackUnitTO
import ro.jf.funds.analytics.api.model.DashboardQueryTO
import ro.jf.funds.analytics.api.model.DashboardTO
import ro.jf.funds.analytics.api.model.GroupingCriteria
import ro.jf.funds.analytics.api.model.MetricTO
import ro.jf.funds.analytics.api.model.ReportFilterTO
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.api.model.UpdateDashboardChartPositionsTO
import ro.jf.funds.analytics.api.model.UpdateDashboardChartTO
import ro.jf.funds.analytics.api.model.UpdateDashboardPositionsTO
import ro.jf.funds.analytics.api.model.UpdateDashboardTO
import ro.jf.funds.analytics.service.config.analyticsDependencies
import ro.jf.funds.analytics.service.config.configureAnalyticsErrorHandling
import ro.jf.funds.analytics.service.config.configureAnalyticsRouting
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.jvm.config.configureContentNegotiation
import ro.jf.funds.platform.jvm.config.configureDatabaseMigration
import ro.jf.funds.platform.jvm.config.configureDependencies
import ro.jf.funds.platform.jvm.test.extension.KafkaContainerExtension
import ro.jf.funds.platform.jvm.test.extension.PostgresContainerExtension
import ro.jf.funds.platform.jvm.test.utils.*
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class DashboardApiTest {

    private val userId = uuid4()
    private val otherUserId = uuid4()
    private val fundId = uuid4()

    private val conversionServiceConfig = MapApplicationConfig(
        "integration.conversion-service.base-url" to "http://localhost:0",
    )
    private val conversionSdk = mock<ConversionSdk>()

    private val basePath = "/funds-api/analytics/v1/dashboards"

    private fun createDashboardRequest(
        name: String = "Wealth",
        charts: List<CreateDashboardChartTO> = emptyList(),
    ) = CreateDashboardTO(
        name = name,
        defaultGranularity = TimeGranularity.MONTHLY,
        defaultLookback = DashboardLookbackTO(amount = 12, unit = DashboardLookbackUnitTO.MONTH),
        defaultTargetCurrency = Currency.EUR,
        charts = charts,
    )

    private fun updateDashboardRequest(name: String = "Wealth") = UpdateDashboardTO(
        name = name,
        defaultGranularity = TimeGranularity.MONTHLY,
        defaultLookback = DashboardLookbackTO(amount = 12, unit = DashboardLookbackUnitTO.MONTH),
        defaultTargetCurrency = Currency.EUR,
    )

    private fun query(
        id: String = "q1",
        label: String = "Balance by fund",
        metric: MetricTO = MetricTO.BALANCE,
        grouping: GroupingCriteria? = GroupingCriteria.FUND,
        filter: ReportFilterTO = ReportFilterTO(fundIds = listOf(fundId)),
    ) = DashboardQueryTO(id = id, label = label, metric = metric, grouping = grouping, filter = filter)

    private fun chartWrite(
        name: String = "Balance by fund",
        id: Uuid? = null,
        queries: List<DashboardQueryTO> = listOf(query()),
    ) = CreateDashboardChartTO(id = id, name = name, queries = queries)

    private suspend fun HttpClient.createDashboard(
        write: CreateDashboardTO,
        asUser: Uuid = userId,
    ): DashboardTO {
        val response = post(basePath) {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, asUser)
            setBody(write)
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        return response.body()
    }

    @Test
    fun `given created dashboards - when listing and getting - then dashboards are returned in position order with charts preserved`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val client = createJsonHttpClient()
            val first = client.createDashboard(createDashboardRequest(name = "Wealth", charts = listOf(chartWrite())))
            val second = client.createDashboard(createDashboardRequest(name = "Spending"))

            val listed = client.get(basePath) { header(USER_ID_HEADER, userId) }.body<List<DashboardTO>>()

            assertThat(listed.map { it.name }).containsExactly("Wealth", "Spending")
            assertThat(listed.map { it.position }).containsExactly(0, 1)
            assertThat(listed.first().defaultLookback)
                .isEqualTo(DashboardLookbackTO(amount = 12, unit = DashboardLookbackUnitTO.MONTH))
            assertThat(listed.first().defaultTargetCurrency).isEqualTo(Currency.EUR)

            val fetched = client.get("$basePath/${first.id}") { header(USER_ID_HEADER, userId) }.body<DashboardTO>()
            val chart = fetched.charts.single()
            assertThat(chart.name).isEqualTo("Balance by fund")
            assertThat(chart.queries.single().id).isEqualTo("q1")
            assertThat(chart.queries.single().label).isEqualTo("Balance by fund")
            assertThat(chart.queries.single().metric).isEqualTo(MetricTO.BALANCE)
            assertThat(chart.queries.single().grouping).isEqualTo(GroupingCriteria.FUND)
            assertThat(chart.queries.single().filter.fundIds).containsExactly(fundId)
            assertThat(second.charts).isEmpty()
        }

    @Test
    fun `given a dashboard with charts - when updating dashboard metadata - then defaults change and charts are untouched`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val client = createJsonHttpClient()
            val created = client.createDashboard(createDashboardRequest(charts = listOf(chartWrite())))

            val response = client.put("$basePath/${created.id}") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(updateDashboardRequest(name = "Renamed"))
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val updated = response.body<DashboardTO>()
            assertThat(updated.name).isEqualTo("Renamed")
            assertThat(updated.charts.map { it.id }).isEqualTo(created.charts.map { it.id })
        }

    @Test
    fun `given a dashboard chart - when updating it - then name and queries change but position is kept`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val client = createJsonHttpClient()
            val created = client.createDashboard(
                createDashboardRequest(charts = listOf(chartWrite(name = "First"), chartWrite(name = "Second")))
            )
            val secondChart = created.charts.last()

            val editedQueries = listOf(query(id = "q2", label = "Profit", metric = MetricTO.TOTAL_PROFIT, grouping = null, filter = ReportFilterTO()))
            val response = client.put("$basePath/${created.id}/charts/${secondChart.id}") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(UpdateDashboardChartTO(name = "Edited", queries = editedQueries))
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val updatedChart = response.body<DashboardChartTO>()
            assertThat(updatedChart.id).isEqualTo(secondChart.id)
            assertThat(updatedChart.name).isEqualTo("Edited")
            assertThat(updatedChart.position).isEqualTo(1)
            assertThat(updatedChart.queries).isEqualTo(editedQueries)
            val fetched = client.get("$basePath/${created.id}") { header(USER_ID_HEADER, userId) }.body<DashboardTO>()
            assertThat(fetched.charts.map { it.name }).containsExactly("First", "Edited")
        }

    @Test
    fun `given a dashboard with charts - when deleting one chart - then only that chart is removed`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val client = createJsonHttpClient()
            val created = client.createDashboard(
                createDashboardRequest(charts = listOf(chartWrite(name = "First"), chartWrite(name = "Second")))
            )
            val firstChart = created.charts.first()

            val response = client.delete("$basePath/${created.id}/charts/${firstChart.id}") {
                header(USER_ID_HEADER, userId)
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
            val fetched = client.get("$basePath/${created.id}") { header(USER_ID_HEADER, userId) }.body<DashboardTO>()
            assertThat(fetched.charts.map { it.name }).containsExactly("Second")
        }

    @Test
    fun `given a dashboard with charts - when reordering charts - then positions follow the new order`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val client = createJsonHttpClient()
            val created = client.createDashboard(
                createDashboardRequest(charts = listOf(chartWrite(name = "First"), chartWrite(name = "Second")))
            )
            val (firstChart, secondChart) = created.charts

            val response = client.put("$basePath/${created.id}/charts/positions") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(UpdateDashboardChartPositionsTO(listOf(secondChart.id, firstChart.id)))
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val reordered = response.body<List<DashboardChartTO>>()
            assertThat(reordered.map { it.id }).containsExactly(secondChart.id, firstChart.id)
            assertThat(reordered.map { it.position }).containsExactly(0, 1)

            val missingId = client.put("$basePath/${created.id}/charts/positions") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody("""{"chartIds":["${firstChart.id}"]}""")
            }
            assertThat(missingId.status).isEqualTo(HttpStatusCode.BadRequest)
        }

    @Test
    fun `given three dashboards - when reordering - then the list follows the new order with compacted positions`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val client = createJsonHttpClient()
            val first = client.createDashboard(createDashboardRequest(name = "First"))
            val second = client.createDashboard(createDashboardRequest(name = "Second"))
            val third = client.createDashboard(createDashboardRequest(name = "Third"))

            val response = client.put("$basePath/positions") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(UpdateDashboardPositionsTO(listOf(third.id, first.id, second.id)))
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val reordered = response.body<List<DashboardTO>>()
            assertThat(reordered.map { it.name }).containsExactly("Third", "First", "Second")
            assertThat(reordered.map { it.position }).containsExactly(0, 1, 2)
            val listed = client.get(basePath) { header(USER_ID_HEADER, userId) }.body<List<DashboardTO>>()
            assertThat(listed.map { it.id }).isEqualTo(reordered.map { it.id })
        }

    @Test
    fun `given two dashboards - when reordering with missing, unknown, or duplicate ids - then responds bad request and order is unchanged`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val client = createJsonHttpClient()
            val first = client.createDashboard(createDashboardRequest(name = "First"))
            val second = client.createDashboard(createDashboardRequest(name = "Second"))

            suspend fun reorder(payload: String, asUser: Uuid = userId) = client.put("$basePath/positions") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, asUser)
                setBody(payload)
            }

            val missingId = reorder("""{"dashboardIds":["${first.id}"]}""")
            assertThat(missingId.status).isEqualTo(HttpStatusCode.BadRequest)

            val unknownId = reorder("""{"dashboardIds":["${first.id}","${second.id}","${uuid4()}"]}""")
            assertThat(unknownId.status).isEqualTo(HttpStatusCode.BadRequest)

            val duplicateIds = reorder("""{"dashboardIds":["${first.id}","${first.id}"]}""")
            assertThat(duplicateIds.status).isEqualTo(HttpStatusCode.BadRequest)

            val foreignUser = reorder("""{"dashboardIds":["${second.id}","${first.id}"]}""", asUser = otherUserId)
            assertThat(foreignUser.status).isEqualTo(HttpStatusCode.BadRequest)

            val listed = client.get(basePath) { header(USER_ID_HEADER, userId) }.body<List<DashboardTO>>()
            assertThat(listed.map { it.name }).containsExactly("First", "Second")
        }

    @Test
    fun `given a dashboard - when deleting - then it is gone with its charts`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val client = createJsonHttpClient()
            val created = client.createDashboard(createDashboardRequest(charts = listOf(chartWrite())))

            val deleteResponse = client.delete("$basePath/${created.id}") { header(USER_ID_HEADER, userId) }

            assertThat(deleteResponse.status).isEqualTo(HttpStatusCode.NoContent)
            val getResponse = client.get("$basePath/${created.id}") { header(USER_ID_HEADER, userId) }
            assertThat(getResponse.status).isEqualTo(HttpStatusCode.NotFound)
            val listed = client.get(basePath) { header(USER_ID_HEADER, userId) }.body<List<DashboardTO>>()
            assertThat(listed).isEmpty()
        }

    @Test
    fun `given a dashboard with charts - when appending a chart - then it is placed last and others are unchanged`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val client = createJsonHttpClient()
            val created = client.createDashboard(
                createDashboardRequest(charts = listOf(chartWrite(name = "First"), chartWrite(name = "Second")))
            )

            val response = client.post("$basePath/${created.id}/charts") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(chartWrite(name = "Appended", queries = listOf(query(id = "q9", label = "Profit", metric = MetricTO.TOTAL_PROFIT))))
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.Created)
            val appended = response.body<DashboardChartTO>()
            assertThat(appended.position).isEqualTo(2)
            val fetched = client.get("$basePath/${created.id}") { header(USER_ID_HEADER, userId) }.body<DashboardTO>()
            assertThat(fetched.charts.map { it.name }).containsExactly("First", "Second", "Appended")
            assertThat(fetched.charts.take(2).map { it.id }).isEqualTo(created.charts.map { it.id })
        }

    @Test
    fun `given invalid payloads - when creating dashboards - then responds bad request`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val client = createJsonHttpClient()

            suspend fun post(payload: String) = client.post(basePath) {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(payload)
            }

            val blankName =
                post("""{"name":" ","defaultGranularity":"MONTHLY","defaultLookback":{"amount":12,"unit":"MONTH"},"defaultTargetCurrency":"EUR"}""")
            assertThat(blankName.status).isEqualTo(HttpStatusCode.BadRequest)

            val nonPositiveLookback =
                post("""{"name":"Wealth","defaultGranularity":"MONTHLY","defaultLookback":{"amount":0,"unit":"MONTH"},"defaultTargetCurrency":"EUR"}""")
            assertThat(nonPositiveLookback.status).isEqualTo(HttpStatusCode.BadRequest)

            val emptyQueries =
                post("""{"name":"Wealth","defaultGranularity":"MONTHLY","defaultLookback":{"amount":12,"unit":"MONTH"},"defaultTargetCurrency":"EUR","charts":[{"name":"Chart","queries":[]}]}""")
            assertThat(emptyQueries.status).isEqualTo(HttpStatusCode.BadRequest)

            val duplicateQueryIds =
                post("""{"name":"Wealth","defaultGranularity":"MONTHLY","defaultLookback":{"amount":12,"unit":"MONTH"},"defaultTargetCurrency":"EUR","charts":[{"name":"Chart","queries":[{"id":"q1","label":"A","metric":"BALANCE"},{"id":"q1","label":"B","metric":"NET_CHANGE"}]}]}""")
            assertThat(duplicateQueryIds.status).isEqualTo(HttpStatusCode.BadRequest)

            val blankLabel =
                post("""{"name":"Wealth","defaultGranularity":"MONTHLY","defaultLookback":{"amount":12,"unit":"MONTH"},"defaultTargetCurrency":"EUR","charts":[{"name":"Chart","queries":[{"id":"q1","label":" ","metric":"BALANCE"}]}]}""")
            assertThat(blankLabel.status).isEqualTo(HttpStatusCode.BadRequest)
        }

    @Test
    fun `given another user's dashboard - when accessing it - then responds not found and data is unchanged`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val client = createJsonHttpClient()
            val created = client.createDashboard(createDashboardRequest(charts = listOf(chartWrite())))
            val chartId = created.charts.single().id

            val foreignGet = client.get("$basePath/${created.id}") { header(USER_ID_HEADER, otherUserId) }
            assertThat(foreignGet.status).isEqualTo(HttpStatusCode.NotFound)

            val foreignPut = client.put("$basePath/${created.id}") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, otherUserId)
                setBody(updateDashboardRequest(name = "Hijacked"))
            }
            assertThat(foreignPut.status).isEqualTo(HttpStatusCode.NotFound)

            val foreignDelete = client.delete("$basePath/${created.id}") { header(USER_ID_HEADER, otherUserId) }
            assertThat(foreignDelete.status).isEqualTo(HttpStatusCode.NotFound)

            val foreignAppend = client.post("$basePath/${created.id}/charts") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, otherUserId)
                setBody(chartWrite(name = "Injected"))
            }
            assertThat(foreignAppend.status).isEqualTo(HttpStatusCode.NotFound)

            val foreignChartPut = client.put("$basePath/${created.id}/charts/$chartId") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, otherUserId)
                setBody(UpdateDashboardChartTO(name = "Hijacked", queries = listOf(query())))
            }
            assertThat(foreignChartPut.status).isEqualTo(HttpStatusCode.NotFound)

            val unknownChartPut = client.put("$basePath/${created.id}/charts/${uuid4()}") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(UpdateDashboardChartTO(name = "Ghost", queries = listOf(query())))
            }
            assertThat(unknownChartPut.status).isEqualTo(HttpStatusCode.NotFound)

            val foreignChartDelete = client.delete("$basePath/${created.id}/charts/$chartId") {
                header(USER_ID_HEADER, otherUserId)
            }
            assertThat(foreignChartDelete.status).isEqualTo(HttpStatusCode.NotFound)

            val stillThere = client.get("$basePath/${created.id}") { header(USER_ID_HEADER, userId) }.body<DashboardTO>()
            assertThat(stillThere.name).isEqualTo("Wealth")
            assertThat(stillThere.charts).hasSize(1)
        }

    private fun Application.testModule() {
        configureDependencies(
            analyticsDependencies,
            module { single<ConversionSdk> { conversionSdk } },
        )
        configureContentNegotiation()
        configureAnalyticsErrorHandling()
        configureDatabaseMigration(get<DataSource>())
        configureAnalyticsRouting()
    }
}
