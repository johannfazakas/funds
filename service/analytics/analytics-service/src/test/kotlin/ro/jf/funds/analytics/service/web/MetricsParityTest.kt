package ro.jf.funds.analytics.service.web

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.client.*
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
import ro.jf.funds.analytics.api.model.*
import ro.jf.funds.analytics.service.config.analyticsDependencies
import ro.jf.funds.analytics.service.config.configureAnalyticsRouting
import ro.jf.funds.analytics.service.domain.AnalyticsRecord
import ro.jf.funds.analytics.api.model.MetricTO
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.Category
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
class MetricsParityTest {
    private val database = PostgresContainerExtension.connection
    private val analyticsRecordRepository = AnalyticsRecordRepository(database)

    private val walletUserId = uuid4()
    private val investmentUserId = uuid4()
    private val fund1 = uuid4()
    private val fund2 = uuid4()
    private val account1 = uuid4()
    private val account2 = uuid4()

    private val eur = Currency("EUR")
    private val sxr8 = Instrument("SXR8")
    private val vt = Instrument("VT")

    private val from = LocalDateTime.parse("2024-01-01T00:00:00")
    private val to = LocalDateTime.parse("2024-04-01T00:00:00")
    private val groupings = listOf(null) + GroupingCriteria.entries

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
        seedRates()
    }

    private fun givenRate(source: FinancialUnit, target: Currency, date: String, rate: String) {
        mockRates[Triple(source, target, LocalDate.parse(date))] = BigDecimal.parseString(rate)
    }

    private fun seedRates() {
        givenRate(eur, Currency.RON, "2023-06-01", "4.90")
        givenRate(eur, Currency.RON, "2024-01-01", "5.00")
        givenRate(eur, Currency.RON, "2024-02-01", "5.10")
        givenRate(eur, Currency.RON, "2024-03-01", "5.20")

        givenRate(sxr8, Currency.RON, "2024-01-01", "550.00")
        givenRate(sxr8, Currency.RON, "2024-02-01", "560.00")
        givenRate(sxr8, Currency.RON, "2024-03-01", "570.00")
        givenRate(vt, Currency.RON, "2024-01-01", "110.00")
        givenRate(vt, Currency.RON, "2024-02-01", "120.00")
        givenRate(vt, Currency.RON, "2024-03-01", "125.00")

        givenRate(sxr8, eur, "2024-01-01", "110.00")
        givenRate(sxr8, eur, "2024-02-01", "112.00")
        givenRate(sxr8, eur, "2024-03-01", "114.00")
        givenRate(vt, eur, "2024-01-01", "22.00")
        givenRate(vt, eur, "2024-02-01", "23.50")
        givenRate(vt, eur, "2024-03-01", "24.00")
        givenRate(Currency.RON, eur, "2024-01-01", "0.20")
    }

    private suspend fun seedWalletRecords() {
        analyticsRecordRepository.saveAll(
            listOf(
                record(walletUserId, "2023-12-15T10:00:00", "500.00", Currency.RON, fund1, account1, "food"),
                record(walletUserId, "2023-12-20T10:00:00", "100.00", eur, fund2, account2, "travel"),
                record(walletUserId, "2024-01-10T10:00:00", "100.00", Currency.RON, fund1, account1, "food"),
                record(walletUserId, "2024-01-15T10:00:00", "50.00", eur, fund2, account2, "travel"),
                record(walletUserId, "2024-02-20T10:00:00", "-30.00", Currency.RON, fund1, account1, null),
                record(walletUserId, "2024-03-15T10:00:00", "200.00", Currency.RON, fund2, account1, "food"),
            )
        )
    }

    private suspend fun seedInvestmentRecords() {
        analyticsRecordRepository.saveAll(
            positionRecords(
                investmentUserId, "2023-06-01T10:00:00", TransactionType.OPEN_POSITION,
                eur, "-1000.00", sxr8, "10.00", fund1, account1, "etf",
            ) + positionRecords(
                investmentUserId, "2024-01-01T00:00:00", TransactionType.OPEN_POSITION,
                Currency.RON, "-2000.00", vt, "20.00", fund2, account2, "etf2",
            ) + positionRecords(
                investmentUserId, "2024-02-15T10:00:00", TransactionType.CLOSE_POSITION,
                eur, "300.00", sxr8, "-2.00", fund1, account1, "etf",
            )
        )
    }

    @Test
    fun `given wallet records - when comparing balance metric with legacy report - then values match per bucket and group`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            seedWalletRecords()
            val client = createJsonHttpClient()

            for (groupBy in groupings) {
                val legacy = client.legacyReport<BigDecimal>("balance", walletUserId, Currency.RON, groupBy)
                val metrics = client.metricsReport(walletUserId, listOf(MetricTO.BALANCE), Currency.RON, groupBy)

                assertParity(legacy, metrics, MetricTO.BALANCE, groupBy) { it }
            }
        }

    @Test
    fun `given wallet records - when comparing net change metric with legacy report - then values match per bucket and group`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            seedWalletRecords()
            val client = createJsonHttpClient()

            for (groupBy in groupings) {
                val legacy = client.legacyReport<BigDecimal>("net-change", walletUserId, Currency.RON, groupBy)
                val metrics = client.metricsReport(walletUserId, listOf(MetricTO.NET_CHANGE), Currency.RON, groupBy)

                assertParity(legacy, metrics, MetricTO.NET_CHANGE, groupBy) { it }
            }
        }

    @Test
    fun `given multi-currency investments with close position - when comparing performance metrics with legacy report - then values match per bucket and group`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            seedInvestmentRecords()
            val client = createJsonHttpClient()
            val performanceMetrics = listOf(
                MetricTO.TOTAL_INVESTMENT,
                MetricTO.CURRENT_INVESTMENT,
                MetricTO.TOTAL_INSTRUMENT_VALUE,
                MetricTO.CURRENCY_VALUE,
                MetricTO.TOTAL_PROFIT,
                MetricTO.CURRENT_PROFIT,
            )

            for (groupBy in groupings) {
                val legacy = client.legacyReport<PerformanceDataTO>("performance", investmentUserId, Currency.RON, groupBy)
                val metrics = client.metricsReport(investmentUserId, performanceMetrics, Currency.RON, groupBy)

                assertParity(legacy, metrics, MetricTO.TOTAL_INVESTMENT, groupBy) { it.totalInvestment }
                assertParity(legacy, metrics, MetricTO.CURRENT_INVESTMENT, groupBy) { it.currentInvestment }
                assertParity(legacy, metrics, MetricTO.TOTAL_INSTRUMENT_VALUE, groupBy) { it.totalInstrumentValue }
                assertParity(legacy, metrics, MetricTO.CURRENCY_VALUE, groupBy) { it.currencyValue }
                assertParity(legacy, metrics, MetricTO.TOTAL_PROFIT, groupBy) { it.totalProfit }
                assertParity(legacy, metrics, MetricTO.CURRENT_PROFIT, groupBy) { it.currentProfit }
            }
        }

    @Test
    fun `given multi-currency investments - when comparing interest rate metrics with legacy report - then values match per bucket and group`(): Unit =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
            seedInvestmentRecords()
            val client = createJsonHttpClient()
            val interestMetrics = listOf(MetricTO.TOTAL_INTEREST_RATE, MetricTO.CURRENT_INTEREST_RATE)

            for (groupBy in groupings) {
                val legacy = client.legacyReport<InterestRateDataTO>("interest-rate", investmentUserId, eur, groupBy)
                val metrics = client.metricsReport(investmentUserId, interestMetrics, eur, groupBy)

                assertParity(legacy, metrics, MetricTO.TOTAL_INTEREST_RATE, groupBy) { it.totalInterestRate }
                assertParity(legacy, metrics, MetricTO.CURRENT_INTEREST_RATE, groupBy) { it.currentInterestRate }
            }
        }

    private fun <T> assertParity(
        legacy: AnalyticsReportTO<T>,
        metrics: MetricsReportTO,
        metric: MetricTO,
        groupBy: GroupingCriteria?,
        extractor: (T) -> BigDecimal,
    ) {
        assertThat(metrics.buckets)
            .describedAs("buckets for metric %s groupBy %s", metric, groupBy)
            .isEqualTo(legacy.buckets.map { it.dateTime })
        legacy.buckets.forEachIndexed { bucketIndex, bucket ->
            bucket.groups.forEach { group ->
                val newValue = metrics.value(metric, bucketIndex, group.groupKey)
                assertThat(newValue)
                    .describedAs(
                        "metric %s bucket %s group %s groupBy %s",
                        metric, bucket.dateTime, group.groupKey, groupBy,
                    )
                    .isEqualByComparingTo(extractor(group.value))
            }
        }
    }

    private fun MetricsReportTO.value(metric: MetricTO, bucketIndex: Int, groupKey: String): BigDecimal =
        series.first { it.metric == metric }
            .groups.firstOrNull { it.groupKey == groupKey }
            ?.values?.get(bucketIndex)
            ?: BigDecimal.ZERO

    private suspend inline fun <reified T> HttpClient.legacyReport(
        report: String,
        userId: Uuid,
        targetCurrency: Currency,
        groupBy: GroupingCriteria?,
    ): AnalyticsReportTO<T> {
        val response = post("/funds-api/analytics/v1/reports/$report") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(
                AnalyticsReportRequestTO(
                    granularity = TimeGranularity.MONTHLY,
                    from = from, to = to,
                    targetCurrency = targetCurrency,
                    groupBy = groupBy,
                )
            )
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        return response.body()
    }

    private suspend fun HttpClient.metricsReport(
        userId: Uuid,
        metrics: List<MetricTO>,
        targetCurrency: Currency,
        groupBy: GroupingCriteria?,
    ): MetricsReportTO {
        val response = post("/funds-api/analytics/v1/metrics") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(
                MetricsReportRequestTO(
                    metrics = metrics,
                    interval = ReportIntervalTO(granularity = TimeGranularity.MONTHLY, from = from, to = to),
                    targetCurrency = targetCurrency,
                    grouping = groupBy,
                )
            )
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        return response.body()
    }

    private fun record(
        userId: Uuid,
        dateTime: String,
        amount: String,
        unit: FinancialUnit,
        fundId: Uuid,
        accountId: Uuid,
        category: String?,
        transactionType: TransactionType = TransactionType.SINGLE_RECORD,
        transactionId: Uuid = uuid4(),
    ) = AnalyticsRecord(
        id = uuid4(),
        userId = userId,
        fundId = fundId,
        accountId = accountId,
        transactionId = transactionId,
        transactionType = transactionType,
        dateTime = LocalDateTime.parse(dateTime),
        amount = BigDecimal.parseString(amount),
        unit = unit,
        category = category?.let { Category(it) },
    )

    private fun positionRecords(
        userId: Uuid,
        dateTime: String,
        transactionType: TransactionType,
        currencyUnit: Currency,
        currencyAmount: String,
        instrumentUnit: Instrument,
        instrumentAmount: String,
        fundId: Uuid,
        accountId: Uuid,
        category: String?,
    ): List<AnalyticsRecord> {
        val transactionId = uuid4()
        return listOf(
            record(userId, dateTime, currencyAmount, currencyUnit, fundId, accountId, category, transactionType, transactionId),
            record(userId, dateTime, instrumentAmount, instrumentUnit, fundId, accountId, category, transactionType, transactionId),
        )
    }

    private fun Application.testModule() {
        configureDependencies(
            analyticsDependencies,
            module { single<ConversionSdk> { conversionSdk } },
        )
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureAnalyticsRouting()
    }
}
