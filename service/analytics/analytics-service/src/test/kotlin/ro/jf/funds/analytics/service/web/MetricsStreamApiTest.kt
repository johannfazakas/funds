package ro.jf.funds.analytics.service.web

import com.benasher44.uuid.uuid4
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.analytics.api.model.GroupingCriteria
import ro.jf.funds.analytics.api.model.MetricQueryTO
import ro.jf.funds.analytics.api.model.MetricTO
import ro.jf.funds.analytics.api.model.MetricsReportRequestTO
import ro.jf.funds.analytics.api.model.MetricsReportTO
import ro.jf.funds.analytics.api.model.MetricsStreamBucketsTO
import ro.jf.funds.analytics.api.model.MetricsStreamErrorTO
import ro.jf.funds.analytics.api.model.MetricsStreamValueTO
import ro.jf.funds.analytics.api.model.ReportIntervalTO
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.config.analyticsDependencies
import ro.jf.funds.analytics.service.config.configureAnalyticsErrorHandling
import ro.jf.funds.analytics.service.config.configureAnalyticsRouting
import ro.jf.funds.analytics.service.domain.AnalyticsRecord
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Instrument
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
class MetricsStreamApiTest {
    private val database = PostgresContainerExtension.connection
    private val analyticsRecordRepository = AnalyticsRecordRepository(database)

    private val userId = uuid4()
    private val accountId = uuid4()
    private val fundId = uuid4()
    private val otherFundId = uuid4()

    private val conversionServiceConfig = MapApplicationConfig(
        "integration.conversion-service.base-url" to "http://localhost:0",
    )

    private val conversionSdk = mock<ConversionSdk>()
    private val mockRates: MutableMap<Triple<FinancialUnit, Currency, LocalDate>, BigDecimal> = mutableMapOf()

    private val json = Json

    @BeforeEach
    fun setupConversionSdkMock(): Unit = runBlocking {
        mockRates.clear()
        whenever(conversionSdk.convert(any())).thenAnswer { invocation ->
            val request = invocation.arguments[0] as ConversionsRequest
            ConversionsResponse(request.conversions.map { conversion ->
                val rate = if (conversion.sourceUnit == conversion.targetCurrency) BigDecimal.ONE
                else mockRates[Triple(conversion.sourceUnit, conversion.targetCurrency, conversion.date)]
                    ?: error("No mock rate configured for $conversion")
                ConversionResponse(conversion.sourceUnit, conversion.targetCurrency, conversion.date, rate)
            })
        }
    }

    private data class SseEvent(val event: String, val data: String)

    private fun parseSseEvents(raw: String): List<SseEvent> = raw
        .replace("\r\n", "\n")
        .split("\n\n")
        .filter { it.isNotBlank() }
        .map { frame ->
            val lines = frame.lines()
            SseEvent(
                event = lines.first { it.startsWith("event:") }.removePrefix("event:").trim(),
                data = lines.filter { it.startsWith("data:") }
                    .joinToString("\n") { it.removePrefix("data:").trim() },
            )
        }

    private fun streamRequest(queries: List<MetricQueryTO>) = MetricsReportRequestTO(
        interval = ReportIntervalTO(
            granularity = TimeGranularity.MONTHLY,
            from = LocalDateTime.parse("2024-01-01T00:00:00"),
            to = LocalDateTime.parse("2024-04-01T00:00:00"),
        ),
        targetCurrency = Currency.RON,
        queries = queries,
    )

    @Test
    fun `given records in two funds - when streaming two queries - then events arrive in protocol order and match the aggregate report`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            analyticsRecordRepository.saveAll(
                listOf(
                    analyticsRecord(dateTime = LocalDateTime.parse("2023-12-01T10:00:00"), amount = "300.00"),
                    analyticsRecord(dateTime = LocalDateTime.parse("2023-12-01T10:00:00"), amount = "200.00", recordFundId = otherFundId),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-01-10T10:00:00"), amount = "100.00"),
                )
            )
            val request = streamRequest(
                listOf(
                    MetricQueryTO(id = "by-fund", metric = MetricTO.BALANCE, grouping = GroupingCriteria.FUND),
                    MetricQueryTO(id = "plain", metric = MetricTO.BALANCE),
                )
            )
            val client = createJsonHttpClient()

            val response = client.post("/funds-api/analytics/v1/metrics/stream") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(request)
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.contentType()?.withoutParameters()).isEqualTo(ContentType.Text.EventStream)
            val events = parseSseEvents(response.bodyAsText())

            assertThat(events.first().event).isEqualTo("buckets")
            val buckets = json.decodeFromString<MetricsStreamBucketsTO>(events.first().data)
            assertThat(buckets.granularity).isEqualTo(TimeGranularity.MONTHLY)
            assertThat(buckets.buckets).containsExactly(
                LocalDateTime.parse("2024-01-01T00:00:00"),
                LocalDateTime.parse("2024-02-01T00:00:00"),
                LocalDateTime.parse("2024-03-01T00:00:00"),
            )

            assertThat(events.last().event).isEqualTo("complete")
            val valueEvents = events.drop(1).dropLast(1)
            assertThat(valueEvents).allSatisfy { assertThat(it.event).isEqualTo("value") }
            val values = valueEvents.map { json.decodeFromString<MetricsStreamValueTO>(it.data) }
            val valuesByQuery = values.groupBy { it.queryId }
            assertThat(valuesByQuery.keys).containsExactlyInAnyOrder("by-fund", "plain")
            valuesByQuery.forEach { (queryId, queryValues) ->
                assertThat(queryValues.map { it.bucket })
                    .describedAs("bucket order for query %s", queryId)
                    .isEqualTo(buckets.buckets)
            }

            val aggregate = client.post("/funds-api/analytics/v1/metrics") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(request)
            }.body<MetricsReportTO>()
            aggregate.series.forEach { series ->
                val streamed = valuesByQuery.getValue(series.queryId)
                series.groups.forEach { group ->
                    aggregate.buckets.forEachIndexed { bucketIndex, bucket ->
                        val streamedValue = streamed.first { it.bucket == bucket }.values[group.groupKey]
                            ?: BigDecimal.ZERO
                        assertThat(streamedValue)
                            .describedAs("query %s group %s bucket %s", series.queryId, group.groupKey, bucket)
                            .isEqualByComparingTo(group.values[bucketIndex])
                    }
                }
            }
        }

    @Test
    fun `given duplicate query ids - when streaming - then responds bad request without starting a stream`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val client = createJsonHttpClient()

            val response = client.post("/funds-api/analytics/v1/metrics/stream") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(
                    """
                    {"interval":{"granularity":"MONTHLY","from":"2024-01-01T00:00:00",
                     "to":"2024-04-01T00:00:00"},"targetCurrency":"RON",
                     "queries":[{"id":"q1","metric":"BALANCE"},{"id":"q1","metric":"NET_CHANGE"}]}
                    """.trimIndent()
                )
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(response.contentType()?.withoutParameters()).isNotEqualTo(ContentType.Text.EventStream)
            assertThat(response.body<ro.jf.funds.platform.jvm.error.ErrorTO>().detail).contains("q1")
        }

    @Test
    fun `given missing conversion rates - when streaming - then a terminal error event is emitted and no complete follows`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val vt = Instrument("VT")
            val transactionId = uuid4()
            analyticsRecordRepository.saveAll(
                listOf(
                    analyticsRecord(
                        dateTime = LocalDateTime.parse("2023-06-01T10:00:00"), amount = "-1000.00",
                        unit = Currency.RON, transactionType = TransactionType.OPEN_POSITION, recordTransactionId = transactionId,
                    ),
                    analyticsRecord(
                        dateTime = LocalDateTime.parse("2023-06-01T10:00:00"), amount = "10.00",
                        unit = vt, transactionType = TransactionType.OPEN_POSITION, recordTransactionId = transactionId,
                    ),
                )
            )
            val client = createJsonHttpClient()

            val response = client.post("/funds-api/analytics/v1/metrics/stream") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(streamRequest(listOf(MetricQueryTO(id = "profit", metric = MetricTO.TOTAL_PROFIT))))
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val events = parseSseEvents(response.bodyAsText())
            assertThat(events.first().event).isEqualTo("buckets")
            assertThat(events.last().event).isEqualTo("error")
            assertThat(events.map { it.event }).doesNotContain("complete")
            val error = json.decodeFromString<MetricsStreamErrorTO>(events.last().data)
            assertThat(error.message).contains("No mock rate configured")
        }

    private fun analyticsRecord(
        dateTime: LocalDateTime,
        amount: String,
        unit: FinancialUnit = Currency.RON,
        transactionType: TransactionType = TransactionType.SINGLE_RECORD,
        recordFundId: com.benasher44.uuid.Uuid = fundId,
        recordTransactionId: com.benasher44.uuid.Uuid = uuid4(),
    ) = AnalyticsRecord(
        id = uuid4(),
        userId = userId,
        fundId = recordFundId,
        accountId = accountId,
        transactionId = recordTransactionId,
        transactionType = transactionType,
        dateTime = dateTime,
        amount = BigDecimal.parseString(amount),
        unit = unit,
        category = null,
    )

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
