package ro.jf.funds.analytics.service.web

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuidFrom
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
class MetricsRegressionTest {
    private val database = PostgresContainerExtension.connection
    private val analyticsRecordRepository = AnalyticsRecordRepository(database)

    private val walletBalanceUser = uuidFrom("11111111-1111-1111-1111-111111111111")
    private val walletNetChangeUser = uuidFrom("11111111-1111-1111-1111-111111111112")
    private val performanceUser = uuidFrom("22222222-2222-2222-2222-222222222221")
    private val interestUser = uuidFrom("22222222-2222-2222-2222-222222222222")
    private val fund1 = uuidFrom("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val fund2 = uuidFrom("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private val account1 = uuidFrom("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val account2 = uuidFrom("dddddddd-dddd-dddd-dddd-dddddddddddd")

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

    private suspend fun seedWalletRecords(userId: Uuid) {
        analyticsRecordRepository.saveAll(
            listOf(
                record(userId, "2023-12-15T10:00:00", "500.00", Currency.RON, fund1, account1, "food"),
                record(userId, "2023-12-20T10:00:00", "100.00", eur, fund2, account2, "travel"),
                record(userId, "2024-01-10T10:00:00", "100.00", Currency.RON, fund1, account1, "food"),
                record(userId, "2024-01-15T10:00:00", "50.00", eur, fund2, account2, "travel"),
                record(userId, "2024-02-20T10:00:00", "-30.00", Currency.RON, fund1, account1, null),
                record(userId, "2024-03-15T10:00:00", "200.00", Currency.RON, fund2, account1, "food"),
            )
        )
    }

    private suspend fun seedInvestmentRecords(userId: Uuid) {
        analyticsRecordRepository.saveAll(
            positionRecords(
                userId, "2023-06-01T10:00:00", TransactionType.OPEN_POSITION,
                eur, "-1000.00", sxr8, "10.00", fund1, account1, "etf",
            ) + positionRecords(
                userId, "2024-01-01T00:00:00", TransactionType.OPEN_POSITION,
                Currency.RON, "-2000.00", vt, "20.00", fund2, account2, "etf2",
            ) + positionRecords(
                userId, "2024-02-15T10:00:00", TransactionType.CLOSE_POSITION,
                eur, "300.00", sxr8, "-2.00", fund1, account1, "etf",
            )
        )
    }

    @Test
    fun `given wallet records - when requesting balance and net change metrics across groupings - then values match the frozen legacy parity expectations`(): Unit = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
        seedWalletRecords(walletBalanceUser)
        seedWalletRecords(walletNetChangeUser)
        val client = createJsonHttpClient()

        for (groupBy in groupings) {
            assertGolden(client, "balance", walletBalanceUser, listOf(MetricTO.BALANCE), Currency.RON, groupBy)
            assertGolden(client, "netChange", walletNetChangeUser, listOf(MetricTO.NET_CHANGE), Currency.RON, groupBy)
        }
    }

    @Test
    fun `given multi-currency investments - when requesting performance and interest rate metrics across groupings - then values match the frozen legacy parity expectations`(): Unit = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)
        seedInvestmentRecords(performanceUser)
        seedInvestmentRecords(interestUser)
        val client = createJsonHttpClient()
        val performanceMetrics = listOf(
            MetricTO.TOTAL_INVESTMENT,
            MetricTO.CURRENT_INVESTMENT,
            MetricTO.TOTAL_INSTRUMENT_VALUE,
            MetricTO.CURRENCY_VALUE,
            MetricTO.TOTAL_PROFIT,
            MetricTO.CURRENT_PROFIT,
        )
        val interestMetrics = listOf(MetricTO.TOTAL_INTEREST_RATE, MetricTO.CURRENT_INTEREST_RATE)

        for (groupBy in groupings) {
            assertGolden(client, "performance", performanceUser, performanceMetrics, Currency.RON, groupBy)
            assertGolden(client, "interestRate", interestUser, interestMetrics, eur, groupBy)
        }
    }

    private suspend fun assertGolden(
        client: HttpClient,
        scenario: String,
        userId: Uuid,
        metrics: List<MetricTO>,
        targetCurrency: Currency,
        groupBy: GroupingCriteria?,
    ) {
        val response = client.post("/funds-api/analytics/v1/metrics") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(
                MetricsReportRequestTO(
                    interval = ReportIntervalTO(granularity = TimeGranularity.MONTHLY, from = from, to = to),
                    targetCurrency = targetCurrency,
                    queries = metrics.map { MetricQueryTO(id = it.name, metric = it, grouping = groupBy) },
                )
            )
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val report = response.body<MetricsReportTO>()
        assertThat(report.buckets).containsExactly(
            LocalDateTime.parse("2024-01-01T00:00:00"),
            LocalDateTime.parse("2024-02-01T00:00:00"),
            LocalDateTime.parse("2024-03-01T00:00:00"),
        )
        val prefix = "$scenario|${groupBy ?: "NONE"}|"
        val expectedByKey = GOLDEN.lines().filter { it.startsWith(prefix) }.associate { line ->
            val parts = line.split("|")
            "${parts[2]}|${parts[3]}" to parts[4].split(",").map { BigDecimal.parseString(it) }
        }
        val actualByKey = report.series.flatMap { series ->
            series.groups.map { group -> Triple(series.metric, group.groupKey, group.values) }
        }
        assertThat(actualByKey.map { (metric, groupKey, _) -> "$metric|$groupKey" })
            .describedAs("series and groups for scenario %s groupBy %s", scenario, groupBy)
            .containsExactlyInAnyOrderElementsOf(expectedByKey.keys)
        actualByKey.forEach { (metric, groupKey, values) ->
            val expected = expectedByKey.getValue("$metric|$groupKey")
            assertThat(values).describedAs("bucket count for %s|%s", metric, groupKey).hasSameSizeAs(expected)
            values.zip(expected).forEachIndexed { bucketIndex, (actualValue, expectedValue) ->
                val description = "metric %s group %s bucket %s groupBy %s".format(metric, groupKey, bucketIndex, groupBy)
                if (metric.unit == MetricUnitTypeTO.PERCENTAGE) {
                    // XIRR bisection sums cash flows in DB read order, so results drift in the
                    // 4th decimal across runs — compare within a tight relative tolerance
                    val expectedDouble = expectedValue.doubleValue(false)
                    if (expectedDouble == 0.0) {
                        assertThat(actualValue.doubleValue(false)).describedAs(description)
                            .isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.001))
                    } else {
                        assertThat(actualValue.doubleValue(false)).describedAs(description)
                            .isCloseTo(expectedDouble, org.assertj.core.data.Percentage.withPercentage(0.5))
                    }
                } else {
                    assertThat(actualValue).describedAs(description).isEqualByComparingTo(expectedValue)
                }
            }
        }
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

    companion object {
        private val GOLDEN = """
            balance|NONE|BALANCE|UNGROUPED|1000,1365,1350
            netChange|NONE|NET_CHANGE|UNGROUPED|350,-30,200
            balance|FINANCIAL_UNIT|BALANCE|EUR|500,765,780
            balance|FINANCIAL_UNIT|BALANCE|RON|500,600,570
            netChange|FINANCIAL_UNIT|NET_CHANGE|EUR|250,0,0
            netChange|FINANCIAL_UNIT|NET_CHANGE|RON|100,-30,200
            balance|ACCOUNT|BALANCE|cccccccc-cccc-cccc-cccc-cccccccccccc|500,600,570
            balance|ACCOUNT|BALANCE|dddddddd-dddd-dddd-dddd-dddddddddddd|500,765,780
            netChange|ACCOUNT|NET_CHANGE|cccccccc-cccc-cccc-cccc-cccccccccccc|100,-30,200
            netChange|ACCOUNT|NET_CHANGE|dddddddd-dddd-dddd-dddd-dddddddddddd|250,0,0
            balance|FUND|BALANCE|aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa|500,600,570
            balance|FUND|BALANCE|bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb|500,765,780
            netChange|FUND|NET_CHANGE|aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa|100,-30,0
            netChange|FUND|NET_CHANGE|bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb|250,0,200
            balance|CATEGORY|BALANCE|UNCATEGORIZED|0,0,-30
            balance|CATEGORY|BALANCE|food|500,600,600
            balance|CATEGORY|BALANCE|travel|500,765,780
            netChange|CATEGORY|NET_CHANGE|UNCATEGORIZED|0,-30,0
            netChange|CATEGORY|NET_CHANGE|food|100,0,200
            netChange|CATEGORY|NET_CHANGE|travel|250,0,0
            performance|NONE|TOTAL_INVESTMENT|UNGROUPED|6900,6900,6900
            performance|NONE|CURRENT_INVESTMENT|UNGROUPED|2000,0,0
            performance|NONE|TOTAL_INSTRUMENT_VALUE|UNGROUPED|7700,6880,7060
            performance|NONE|CURRENCY_VALUE|UNGROUPED|-7000,-5570,-5640
            performance|NONE|TOTAL_PROFIT|UNGROUPED|800,-20,160
            performance|NONE|CURRENT_PROFIT|UNGROUPED|800,-820,180
            interestRate|NONE|TOTAL_INTEREST_RATE|UNGROUPED|25.0474214553834,-4.77944471359254,-0.97848260879516
            interestRate|NONE|CURRENT_INTEREST_RATE|UNGROUPED|0,-75.68003826141358,26.7954635620118
            performance|FINANCIAL_UNIT|TOTAL_INVESTMENT|SXR8|4900,4900,4900
            performance|FINANCIAL_UNIT|TOTAL_INVESTMENT|VT|2000,2000,2000
            performance|FINANCIAL_UNIT|CURRENT_INVESTMENT|VT|2000,0,0
            performance|FINANCIAL_UNIT|TOTAL_INSTRUMENT_VALUE|SXR8|5500,4480,4560
            performance|FINANCIAL_UNIT|TOTAL_INSTRUMENT_VALUE|VT|2200,2400,2500
            performance|FINANCIAL_UNIT|CURRENCY_VALUE|EUR|-5000,-3570,-3640
            performance|FINANCIAL_UNIT|CURRENCY_VALUE|RON|-2000,-2000,-2000
            performance|FINANCIAL_UNIT|TOTAL_PROFIT|SXR8|600,-420,-340
            performance|FINANCIAL_UNIT|TOTAL_PROFIT|VT|200,400,500
            performance|FINANCIAL_UNIT|CURRENT_PROFIT|SXR8|600,-1020,80
            performance|FINANCIAL_UNIT|CURRENT_PROFIT|VT|200,200,100
            interestRate|FINANCIAL_UNIT|TOTAL_INTEREST_RATE|EUR|0,0,0
            interestRate|FINANCIAL_UNIT|TOTAL_INTEREST_RATE|RON|0,0,0
            interestRate|FINANCIAL_UNIT|TOTAL_INTEREST_RATE|SXR8|0,0,0
            interestRate|FINANCIAL_UNIT|TOTAL_INTEREST_RATE|VT|0,0,0
            interestRate|FINANCIAL_UNIT|CURRENT_INTEREST_RATE|EUR|0,0,0
            interestRate|FINANCIAL_UNIT|CURRENT_INTEREST_RATE|RON|0,0,0
            interestRate|FINANCIAL_UNIT|CURRENT_INTEREST_RATE|SXR8|0,-91.0966130065918,24.9655151367188
            interestRate|FINANCIAL_UNIT|CURRENT_INTEREST_RATE|VT|0,117.5968933105468,30.3569030761718
            performance|ACCOUNT|TOTAL_INVESTMENT|cccccccc-cccc-cccc-cccc-cccccccccccc|4900,4900,4900
            performance|ACCOUNT|TOTAL_INVESTMENT|dddddddd-dddd-dddd-dddd-dddddddddddd|2000,2000,2000
            performance|ACCOUNT|CURRENT_INVESTMENT|dddddddd-dddd-dddd-dddd-dddddddddddd|2000,0,0
            performance|ACCOUNT|TOTAL_INSTRUMENT_VALUE|cccccccc-cccc-cccc-cccc-cccccccccccc|5500,4480,4560
            performance|ACCOUNT|TOTAL_INSTRUMENT_VALUE|dddddddd-dddd-dddd-dddd-dddddddddddd|2200,2400,2500
            performance|ACCOUNT|CURRENCY_VALUE|cccccccc-cccc-cccc-cccc-cccccccccccc|-5000,-3570,-3640
            performance|ACCOUNT|CURRENCY_VALUE|dddddddd-dddd-dddd-dddd-dddddddddddd|-2000,-2000,-2000
            performance|ACCOUNT|TOTAL_PROFIT|cccccccc-cccc-cccc-cccc-cccccccccccc|600,-420,-340
            performance|ACCOUNT|TOTAL_PROFIT|dddddddd-dddd-dddd-dddd-dddddddddddd|200,400,500
            performance|ACCOUNT|CURRENT_PROFIT|cccccccc-cccc-cccc-cccc-cccccccccccc|600,-1020,80
            performance|ACCOUNT|CURRENT_PROFIT|dddddddd-dddd-dddd-dddd-dddddddddddd|200,200,100
            interestRate|ACCOUNT|TOTAL_INTEREST_RATE|cccccccc-cccc-cccc-cccc-cccccccccccc|17.6546335220336,-15.09058763504028,-11.54724676132202
            interestRate|ACCOUNT|TOTAL_INTEREST_RATE|dddddddd-dddd-dddd-dddd-dddddddddddd|0,569.5907592773438,203.7640380859375
            interestRate|ACCOUNT|CURRENT_INTEREST_RATE|cccccccc-cccc-cccc-cccc-cccccccccccc|0,-91.0966130065918,24.9655151367188
            interestRate|ACCOUNT|CURRENT_INTEREST_RATE|dddddddd-dddd-dddd-dddd-dddddddddddd|0,117.5968933105468,30.3569030761718
            performance|FUND|TOTAL_INVESTMENT|aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa|4900,4900,4900
            performance|FUND|TOTAL_INVESTMENT|bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb|2000,2000,2000
            performance|FUND|CURRENT_INVESTMENT|bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb|2000,0,0
            performance|FUND|TOTAL_INSTRUMENT_VALUE|aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa|5500,4480,4560
            performance|FUND|TOTAL_INSTRUMENT_VALUE|bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb|2200,2400,2500
            performance|FUND|CURRENCY_VALUE|aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa|-5000,-3570,-3640
            performance|FUND|CURRENCY_VALUE|bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb|-2000,-2000,-2000
            performance|FUND|TOTAL_PROFIT|aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa|600,-420,-340
            performance|FUND|TOTAL_PROFIT|bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb|200,400,500
            performance|FUND|CURRENT_PROFIT|aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa|600,-1020,80
            performance|FUND|CURRENT_PROFIT|bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb|200,200,100
            interestRate|FUND|TOTAL_INTEREST_RATE|aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa|17.6546335220336,-15.09058763504028,-11.54724676132202
            interestRate|FUND|TOTAL_INTEREST_RATE|bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb|0,569.5907592773438,203.7640380859375
            interestRate|FUND|CURRENT_INTEREST_RATE|aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa|0,-91.0966130065918,24.9655151367188
            interestRate|FUND|CURRENT_INTEREST_RATE|bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb|0,117.5968933105468,30.3569030761718
            performance|CATEGORY|TOTAL_INVESTMENT|etf|4900,4900,4900
            performance|CATEGORY|TOTAL_INVESTMENT|etf2|2000,2000,2000
            performance|CATEGORY|CURRENT_INVESTMENT|etf2|2000,0,0
            performance|CATEGORY|TOTAL_INSTRUMENT_VALUE|etf|5500,4480,4560
            performance|CATEGORY|TOTAL_INSTRUMENT_VALUE|etf2|2200,2400,2500
            performance|CATEGORY|CURRENCY_VALUE|etf|-5000,-3570,-3640
            performance|CATEGORY|CURRENCY_VALUE|etf2|-2000,-2000,-2000
            performance|CATEGORY|TOTAL_PROFIT|etf|600,-420,-340
            performance|CATEGORY|TOTAL_PROFIT|etf2|200,400,500
            performance|CATEGORY|CURRENT_PROFIT|etf|600,-1020,80
            performance|CATEGORY|CURRENT_PROFIT|etf2|200,200,100
            interestRate|CATEGORY|TOTAL_INTEREST_RATE|etf|17.6546335220336,-15.09058763504028,-11.54724676132202
            interestRate|CATEGORY|TOTAL_INTEREST_RATE|etf2|0,569.5907592773438,203.7640380859375
            interestRate|CATEGORY|CURRENT_INTEREST_RATE|etf|0,-91.0966130065918,24.9655151367188
            interestRate|CATEGORY|CURRENT_INTEREST_RATE|etf2|0,117.5968933105468,30.3569030761718
        """.trimIndent()
    }
}
