package ro.jf.funds.analytics.service.web

import com.benasher44.uuid.uuid4
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
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
import ro.jf.funds.analytics.api.model.MetricInfoTO
import ro.jf.funds.analytics.api.model.MetricQueryTO
import ro.jf.funds.analytics.api.model.ReportFilterTO
import ro.jf.funds.analytics.api.model.MetricUnitTypeTO
import ro.jf.funds.analytics.api.model.MetricsReportRequestTO
import ro.jf.funds.analytics.api.model.MetricsReportTO
import ro.jf.funds.analytics.api.model.ReportIntervalTO
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.config.analyticsDependencies
import ro.jf.funds.analytics.service.config.configureAnalyticsErrorHandling
import ro.jf.funds.analytics.service.config.configureAnalyticsRouting
import ro.jf.funds.analytics.service.domain.AnalyticsRecord
import ro.jf.funds.analytics.api.model.MetricTO
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
class MetricsApiTest {
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

    private fun givenRate(source: FinancialUnit, target: Currency, date: String, rate: String) {
        mockRates[Triple(source, target, LocalDate.parse(date))] = BigDecimal.parseString(rate)
    }

    private fun metricsRequest(
        metrics: List<MetricTO>,
        targetCurrency: Currency = Currency.RON,
        groupBy: GroupingCriteria? = null,
    ) = metricsQueriesRequest(
        queries = metrics.mapIndexed { index, metric ->
            MetricQueryTO(id = "q${index + 1}", metric = metric, grouping = groupBy)
        },
        targetCurrency = targetCurrency,
    )

    private fun metricsQueriesRequest(
        queries: List<MetricQueryTO>,
        targetCurrency: Currency = Currency.RON,
    ) = MetricsReportRequestTO(
        interval = ReportIntervalTO(
            granularity = TimeGranularity.MONTHLY,
            from = LocalDateTime.parse("2024-01-01T00:00:00"),
            to = LocalDateTime.parse("2024-04-01T00:00:00"),
        ),
        targetCurrency = targetCurrency,
        queries = queries,
    )

    @Test
    fun `given analytics records - when requesting single balance metric - then returns cumulative balance series`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            analyticsRecordRepository.saveAll(
                listOf(
                    analyticsRecord(dateTime = LocalDateTime.parse("2023-12-15T10:00:00"), amount = "500.00"),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-01-10T10:00:00"), amount = "100.00"),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-03-15T10:00:00"), amount = "-50.00"),
                )
            )

            val client = createJsonHttpClient()
            val response = client.post("/funds-api/analytics/v1/metrics") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(metricsRequest(listOf(MetricTO.BALANCE)))
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val report = response.body<MetricsReportTO>()
            assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
            assertThat(report.buckets).containsExactly(
                LocalDateTime.parse("2024-01-01T00:00:00"),
                LocalDateTime.parse("2024-02-01T00:00:00"),
                LocalDateTime.parse("2024-03-01T00:00:00"),
            )
            assertThat(report.series).hasSize(1)
            val series = report.series.single()
            assertThat(series.queryId).isEqualTo("q1")
            assertThat(series.metric).isEqualTo(MetricTO.BALANCE)
            assertThat(series.unit).isEqualTo(MetricUnitTypeTO.CURRENCY)
            assertThat(series.currency).isEqualTo(Currency.RON)
            val groups = series.groups.single()
            assertThat(groups.groupKey).isEqualTo("UNGROUPED")
            assertThat(groups.values).containsExactly(
                BigDecimal.parseString("500.00"),
                BigDecimal.parseString("600.00"),
                BigDecimal.parseString("600.00"),
            )
        }

    @Test
    fun `given investment records - when requesting multiple metrics - then returns one series per metric over shared buckets`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val vt = Instrument("VT")
            analyticsRecordRepository.saveAll(
                openPositionRecords(
                    dateTime = LocalDateTime.parse("2023-06-01T10:00:00"),
                    currencyUnit = Currency.RON, currencyAmount = "-1000.00",
                    instrumentUnit = vt, instrumentAmount = "10.00",
                )
            )
            givenRate(vt, Currency.RON, "2024-01-01", "110.00")
            givenRate(vt, Currency.RON, "2024-02-01", "120.00")
            givenRate(vt, Currency.RON, "2024-03-01", "130.00")

            val client = createJsonHttpClient()
            val response = client.post("/funds-api/analytics/v1/metrics") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(metricsRequest(listOf(MetricTO.TOTAL_PROFIT, MetricTO.TOTAL_INTEREST_RATE)))
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val report = response.body<MetricsReportTO>()
            assertThat(report.series.map { it.metric })
                .containsExactly(MetricTO.TOTAL_PROFIT, MetricTO.TOTAL_INTEREST_RATE)

            val profitSeries = report.series.first { it.metric == MetricTO.TOTAL_PROFIT }
            assertThat(profitSeries.unit).isEqualTo(MetricUnitTypeTO.CURRENCY)
            assertThat(profitSeries.groups.single().values).containsExactly(
                BigDecimal.parseString("100.00"),
                BigDecimal.parseString("200.00"),
                BigDecimal.parseString("300.00"),
            )

            val rateSeries = report.series.first { it.metric == MetricTO.TOTAL_INTEREST_RATE }
            assertThat(rateSeries.unit).isEqualTo(MetricUnitTypeTO.PERCENTAGE)
            assertThat(rateSeries.currency).isNull()
            assertThat(rateSeries.groups.single().values).hasSize(3)
            assertThat(rateSeries.groups.single().values[0].doubleValue(false)).isGreaterThan(0.0)
        }

    @Test
    fun `given records in two funds - when requesting grouped balance metric - then returns per-fund series`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            analyticsRecordRepository.saveAll(
                listOf(
                    analyticsRecord(dateTime = LocalDateTime.parse("2023-12-01T10:00:00"), amount = "300.00"),
                    analyticsRecord(dateTime = LocalDateTime.parse("2023-12-01T10:00:00"), amount = "200.00", recordFundId = otherFundId),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-01-10T10:00:00"), amount = "100.00"),
                )
            )

            val client = createJsonHttpClient()
            val response = client.post("/funds-api/analytics/v1/metrics") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(metricsRequest(listOf(MetricTO.BALANCE), groupBy = GroupingCriteria.FUND))
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val series = response.body<MetricsReportTO>().series.single()
            assertThat(series.groups).hasSize(2)
            val fund1Values = series.groups.first { it.groupKey == fundId.toString() }.values
            val fund2Values = series.groups.first { it.groupKey == otherFundId.toString() }.values
            assertThat(fund1Values).containsExactly(
                BigDecimal.parseString("300.00"),
                BigDecimal.parseString("400.00"),
                BigDecimal.parseString("400.00"),
            )
            assertThat(fund2Values).containsExactly(
                BigDecimal.parseString("200.00"),
                BigDecimal.parseString("200.00"),
                BigDecimal.parseString("200.00"),
            )
        }

    @Test
    fun `given records in two funds - when requesting same metric with two query contexts - then returns one series per query`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            analyticsRecordRepository.saveAll(
                listOf(
                    analyticsRecord(dateTime = LocalDateTime.parse("2023-12-01T10:00:00"), amount = "300.00"),
                    analyticsRecord(dateTime = LocalDateTime.parse("2023-12-01T10:00:00"), amount = "200.00", recordFundId = otherFundId),
                    analyticsRecord(dateTime = LocalDateTime.parse("2024-01-10T10:00:00"), amount = "100.00"),
                )
            )

            val client = createJsonHttpClient()
            val response = client.post("/funds-api/analytics/v1/metrics") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(
                    metricsQueriesRequest(
                        listOf(
                            MetricQueryTO(id = "by-fund", metric = MetricTO.BALANCE, grouping = GroupingCriteria.FUND),
                            MetricQueryTO(id = "single-fund", metric = MetricTO.BALANCE, filter = ReportFilterTO(fundIds = listOf(fundId))),
                        )
                    )
                )
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val report = response.body<MetricsReportTO>()
            assertThat(report.series.map { it.queryId }).containsExactly("by-fund", "single-fund")

            val groupedSeries = report.series.first { it.queryId == "by-fund" }
            assertThat(groupedSeries.groups.map { it.groupKey })
                .containsExactlyInAnyOrder(fundId.toString(), otherFundId.toString())

            val filteredSeries = report.series.first { it.queryId == "single-fund" }
            val filteredGroup = filteredSeries.groups.single()
            assertThat(filteredGroup.groupKey).isEqualTo("UNGROUPED")
            assertThat(filteredGroup.values).containsExactly(
                BigDecimal.parseString("300.00"),
                BigDecimal.parseString("400.00"),
                BigDecimal.parseString("400.00"),
            )
        }

    @Test
    fun `given constant conversion rates - when requesting interest rate with different target currencies - then percentage series is identical`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val sxr8 = Instrument("SXR8")
            val eur = Currency("EUR")
            analyticsRecordRepository.saveAll(
                openPositionRecords(
                    dateTime = LocalDateTime.parse("2023-01-01T10:00:00"),
                    currencyUnit = eur, currencyAmount = "-1000.00",
                    instrumentUnit = sxr8, instrumentAmount = "10.00",
                )
            )
            for (date in listOf("2023-01-01", "2024-01-01", "2024-02-01", "2024-03-01")) {
                givenRate(sxr8, eur, date, "110.00")
                givenRate(sxr8, Currency.RON, date, "550.00")
                givenRate(eur, Currency.RON, date, "5.00")
            }

            val client = createJsonHttpClient()
            suspend fun rates(targetCurrency: Currency): List<BigDecimal> {
                val response = client.post("/funds-api/analytics/v1/metrics") {
                    contentType(ContentType.Application.Json)
                    header(USER_ID_HEADER, userId)
                    setBody(metricsRequest(listOf(MetricTO.TOTAL_INTEREST_RATE), targetCurrency = targetCurrency))
                }
                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                return response.body<MetricsReportTO>().series.single().groups.single().values
            }

            val eurRates = rates(eur)
            val ronRates = rates(Currency.RON)
            assertThat(eurRates).hasSameSizeAs(ronRates)
            eurRates.zip(ronRates).forEach { (eurRate, ronRate) ->
                assertThat(eurRate.doubleValue(false))
                    .isCloseTo(ronRate.doubleValue(false), org.assertj.core.data.Offset.offset(0.001))
            }
        }

    @Test
    fun `given invalid metric requests - when posting metrics report - then responds bad request naming offenders`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val client = createJsonHttpClient()

            suspend fun post(queriesJson: String) = client.post("/funds-api/analytics/v1/metrics") {
                contentType(ContentType.Application.Json)
                header(USER_ID_HEADER, userId)
                setBody(
                    """
                    {"interval":{"granularity":"MONTHLY","from":"2024-01-01T00:00:00",
                     "to":"2024-04-01T00:00:00"},"targetCurrency":"RON","queries":$queriesJson}
                    """.trimIndent()
                )
            }

            val unknownResponse = post("""[{"id":"q1","metric":"BALANCE"},{"id":"q2","metric":"BOGUS_METRIC"}]""")
            assertThat(unknownResponse.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(unknownResponse.bodyAsErrorDetail()).contains("BOGUS_METRIC")

            val internalResponse = post("""[{"id":"q1","metric":"PAIRED_POSITIONS"}]""")
            assertThat(internalResponse.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(internalResponse.bodyAsErrorDetail()).contains("PAIRED_POSITIONS")

            val emptyResponse = post("[]")
            assertThat(emptyResponse.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(emptyResponse.bodyAsErrorDetail()).contains("query")

            val duplicateIdsResponse = post("""[{"id":"q1","metric":"BALANCE"},{"id":"q1","metric":"NET_CHANGE"}]""")
            assertThat(duplicateIdsResponse.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(duplicateIdsResponse.bodyAsErrorDetail()).contains("q1")
        }

    @Test
    fun `given metric registry - when listing metrics - then returns exactly the ten external metrics with units`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            val client = createJsonHttpClient()

            val response = client.get("/funds-api/analytics/v1/metrics")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val metrics = response.body<List<MetricInfoTO>>()
            assertThat(metrics).containsExactlyInAnyOrder(
                MetricInfoTO(MetricTO.BALANCE, MetricUnitTypeTO.CURRENCY),
                MetricInfoTO(MetricTO.NET_CHANGE, MetricUnitTypeTO.CURRENCY),
                MetricInfoTO(MetricTO.TOTAL_INVESTMENT, MetricUnitTypeTO.CURRENCY),
                MetricInfoTO(MetricTO.CURRENT_INVESTMENT, MetricUnitTypeTO.CURRENCY),
                MetricInfoTO(MetricTO.TOTAL_INSTRUMENT_VALUE, MetricUnitTypeTO.CURRENCY),
                MetricInfoTO(MetricTO.CURRENCY_VALUE, MetricUnitTypeTO.CURRENCY),
                MetricInfoTO(MetricTO.TOTAL_PROFIT, MetricUnitTypeTO.CURRENCY),
                MetricInfoTO(MetricTO.CURRENT_PROFIT, MetricUnitTypeTO.CURRENCY),
                MetricInfoTO(MetricTO.TOTAL_INTEREST_RATE, MetricUnitTypeTO.PERCENTAGE),
                MetricInfoTO(MetricTO.CURRENT_INTEREST_RATE, MetricUnitTypeTO.PERCENTAGE),
            )
        }

    private suspend fun io.ktor.client.statement.HttpResponse.bodyAsErrorDetail(): String =
        body<ro.jf.funds.platform.jvm.error.ErrorTO>().detail ?: ""

    private fun analyticsRecord(
        dateTime: LocalDateTime,
        amount: String,
        unit: FinancialUnit = Currency("RON"),
        transactionType: TransactionType = TransactionType.SINGLE_RECORD,
        recordFundId: com.benasher44.uuid.Uuid = fundId,
        transactionId: com.benasher44.uuid.Uuid = uuid4(),
    ) = AnalyticsRecord(
        id = uuid4(),
        userId = userId,
        fundId = recordFundId,
        accountId = accountId,
        transactionId = transactionId,
        transactionType = transactionType,
        dateTime = dateTime,
        amount = BigDecimal.parseString(amount),
        unit = unit,
        category = null,
    )

    private fun openPositionRecords(
        dateTime: LocalDateTime,
        currencyUnit: Currency,
        currencyAmount: String,
        instrumentUnit: Instrument,
        instrumentAmount: String,
    ): List<AnalyticsRecord> {
        val transactionId = uuid4()
        return listOf(
            analyticsRecord(dateTime, currencyAmount, currencyUnit, TransactionType.OPEN_POSITION, transactionId = transactionId),
            analyticsRecord(dateTime, instrumentAmount, instrumentUnit, TransactionType.OPEN_POSITION, transactionId = transactionId),
        )
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
