package ro.jf.funds.reporting.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import ro.jf.funds.platform.jvm.config.configureContentNegotiation
import ro.jf.funds.platform.jvm.config.configureDatabaseMigration
import ro.jf.funds.platform.jvm.config.configureDependencies
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Currency.Companion.EUR
import ro.jf.funds.platform.api.model.Currency.Companion.RON
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.platform.api.model.Label
import ro.jf.funds.platform.api.model.ListTO
import ro.jf.funds.platform.api.model.labelsOf
import ro.jf.funds.platform.jvm.test.extension.KafkaContainerExtension
import ro.jf.funds.platform.jvm.test.extension.PostgresContainerExtension
import ro.jf.funds.platform.jvm.test.utils.configureEnvironment
import ro.jf.funds.platform.jvm.test.utils.createJsonHttpClient
import ro.jf.funds.platform.jvm.test.utils.dbConfig
import ro.jf.funds.platform.jvm.test.utils.kafkaConfig
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import ro.jf.funds.fund.api.model.TransactionFilterTO
import ro.jf.funds.fund.api.model.TransactionRecordTO
import ro.jf.funds.fund.api.model.TransactionTO
import ro.jf.funds.fund.sdk.TransactionSdk
import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.service.config.configureReportingErrorHandling
import ro.jf.funds.reporting.service.config.configureReportingRouting
import ro.jf.funds.reporting.service.config.reportingDependencies
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import javax.sql.DataSource
import java.math.BigDecimal as JavaBigDecimal

@ExtendWith(KafkaContainerExtension::class)
@ExtendWith(PostgresContainerExtension::class)
class ReportingApiTest {
    private val reportViewRepository = ReportViewRepository(PostgresContainerExtension.connection)
    private val transactionSdk = mock<TransactionSdk>()
    private val conversionRateService = mock<ConversionRateService>()

    private val userId = uuid4()
    private val expenseFundId = uuid4()
    private val expenseReportName = "Expense Report"
    private val cashAccountId = uuid4()
    private val date = LocalDate(2021, 9, 1)
    private val labels = labelsOf("need", "want")
    private val reportDataConfiguration = ReportDataConfiguration(
        currency = RON,
        groups = null,
        reports = ReportsConfiguration()
            .withNet(enabled = true, filter = RecordFilter(labels))
            .withValueReport(enabled = true, RecordFilter(labels))
    )
    private val reportViewCommand = CreateReportViewCommand(
        userId = userId,
        name = expenseReportName,
        fundId = expenseFundId,
        dataConfiguration = reportDataConfiguration
    )

    @Test
    fun `create report view should create it`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val httpClient = createJsonHttpClient()

        val transaction =
            singleRecordTransaction(
                userId, date,
                record(expenseFundId, cashAccountId, BigDecimal.parseString("-100.0"), RON, labelsOf("need"))
            )
        whenever(transactionSdk.listTransactions(userId, TransactionFilterTO(fundId = expenseFundId)))
            .thenReturn(ListTO.of(transaction))

        val response = httpClient.post("/funds-api/reporting/v1/report-views") {
            header(USER_ID_HEADER, userId.toString())
            contentType(ContentType.Application.Json)
            setBody(
                CreateReportViewTO(
                    name = expenseReportName,
                    fundId = expenseFundId,
                    dataConfiguration = ReportDataConfigurationTO(
                        currency = RON,
                        groups = listOf(
                            ReportGroupTO(
                                name = "Need group",
                                filter = RecordFilterTO.byLabels("need")
                            ),
                            ReportGroupTO(
                                name = "Want group",
                                filter = RecordFilterTO.byLabels("want")
                            )
                        ),
                        reports = ReportsConfigurationTO(
                            net = NetReportConfigurationTO(enabled = true, RecordFilterTO(labels)),
                            valueReport = ValueReportConfigurationTO(true, RecordFilterTO(labels))
                        )
                    )
                )
            )
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val reportViewTO = response.body<ReportViewTO>()

        assertThat(reportViewTO).isNotNull
        assertThat(reportViewTO.name).isEqualTo(expenseReportName)
        assertThat(reportViewTO.fundId).isEqualTo(expenseFundId)
        assertThat(reportViewTO.dataConfiguration.currency).isEqualTo(RON)
        assertThat(reportViewTO.dataConfiguration.groups).hasSize(2)
        assertThat(reportViewTO.dataConfiguration.reports.net.enabled).isTrue()
        assertThat(reportViewTO.dataConfiguration.reports.net.filter?.labels).containsExactlyElementsOf(labels)
    }

    @Test
    fun `get report view`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()
        val reportView = reportViewRepository.save(reportViewCommand)

        val response = httpClient.get("/funds-api/reporting/v1/report-views/${reportView.id}") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val reportViewTO = response.body<ReportViewTO>()
        assertThat(reportViewTO).isNotNull
        assertThat(reportViewTO.id).isEqualTo(reportView.id)
        assertThat(reportViewTO.fundId).isEqualTo(expenseFundId)
        assertThat(reportViewTO.name).isEqualTo(expenseReportName)
    }

    @Test
    fun `list report views`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()
        val reportView = reportViewRepository.save(reportViewCommand)

        val response = httpClient.get("/funds-api/reporting/v1/report-views") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val reportViewTOs = response.body<ListTO<ReportViewTO>>()
        assertThat(reportViewTOs).isNotNull
        assertThat(reportViewTOs.items).hasSize(1)
        val reportViewTO = reportViewTOs.items.first()
        assertThat(reportViewTO.id).isEqualTo(reportView.id)
        assertThat(reportViewTO.fundId).isEqualTo(expenseFundId)
        assertThat(reportViewTO.name).isEqualTo(expenseReportName)
    }

    @Test
    fun `get net data report`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()
        val reportView = reportViewRepository.save(reportViewCommand)
        whenever(conversionRateService.getRate(any(), eq(EUR), eq(RON))).thenReturn(JavaBigDecimal(5))
        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON))).thenReturn(JavaBigDecimal.ONE)

        val fromDate = LocalDate(2021, 1, 1)
        val toDate = LocalDate(2021, 1, 28)
        val interval = ReportDataInterval.Daily(fromDate, toDate, null)
        mockTransactions(
            interval, reportView.fundId, listOf(
                singleRecordTransaction(
                    userId, LocalDate(2021, 1, 2),
                    record(
                        reportView.fundId, cashAccountId, BigDecimal.parseString("-25.0"), RON, labelsOf("need")
                    )
                ),
                singleRecordTransaction(
                    userId, LocalDate(2021, 1, 2),
                    record(
                        reportView.fundId, cashAccountId, BigDecimal.parseString("-10.0"), EUR, labelsOf("want")
                    )
                )
            )
        )

        val response = httpClient.get("/funds-api/reporting/v1/report-views/${reportView.id}/data/net") {
            header(USER_ID_HEADER, userId.toString())
            parameter("granularity", TimeGranularityTO.DAILY.name)
            parameter("fromDate", fromDate.toString())
            parameter("toDate", toDate.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val reportData = response.body<ReportDataTO<NetReportTO>>()
        assertThat(reportData.viewId).isEqualTo(reportView.id)
        assertThat(reportData.timeBuckets).hasSize(28)
        assertThat(reportData.timeBuckets[0])
            .isEqualTo(
                BucketDataTO(
                    timeBucket = DateIntervalTO(LocalDate(2021, 1, 1), LocalDate(2021, 1, 1)),
                    bucketType = BucketTypeTO.REAL,
                    report = NetReportTO(BigDecimal.parseString("0.0")),
                )
            )
        assertThat(reportData.timeBuckets[1])
            .isEqualTo(
                BucketDataTO(
                    timeBucket = DateIntervalTO(LocalDate(2021, 1, 2), LocalDate(2021, 1, 2)),
                    bucketType = BucketTypeTO.REAL,
                    report = NetReportTO(BigDecimal.parseString("-75.0")),
                )
            )
    }

    @Test
    fun `get interest rate data report`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()
        val investmentReportView = reportViewRepository.save(
            reportViewCommand.copy(
                dataConfiguration = reportDataConfiguration.copy(
                    reports = ReportsConfiguration().withInterestRate(enabled = true)
                )
            )
        )
        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON))).thenReturn(JavaBigDecimal.ONE)
        whenever(conversionRateService.getRate(any(), any<Instrument>(), eq(RON))).thenReturn(JavaBigDecimal("100.0"))

        val interval = ReportDataInterval.Monthly(YearMonth(2021, 1), YearMonth(2021, 3), null)
        mockTransactions(
            interval, investmentReportView.fundId, listOf(
                openPositionTransaction(
                    userId, LocalDate(2021, 2, 15),
                    currencyRecord = record(investmentReportView.fundId, cashAccountId, BigDecimal.parseString("-100.0"), RON, labelsOf("need")),
                    instrumentRecord = record(investmentReportView.fundId, cashAccountId, BigDecimal.parseString("1.0"), Instrument("AAPL"), labelsOf("need"))
                )
            )
        )

        val response = httpClient.get("/funds-api/reporting/v1/report-views/${investmentReportView.id}/data/interest-rate") {
            header(USER_ID_HEADER, userId.toString())
            parameter("granularity", TimeGranularityTO.MONTHLY.name)
            parameter("fromYearMonth", "2021-01")
            parameter("toYearMonth", "2021-03")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val reportData = response.body<ReportDataTO<InterestRateReportTO>>()
        assertThat(reportData.viewId).isEqualTo(investmentReportView.id)
        assertThat(reportData.timeBuckets).hasSize(3)
        assertThat(reportData.timeBuckets[0].report.totalInterestRate).isNotNull()
        assertThat(reportData.timeBuckets[0].report.currentInterestRate).isNotNull()
    }

    @Test
    fun `get unit interest rate data report`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()
        val investmentReportView = reportViewRepository.save(
            reportViewCommand.copy(
                dataConfiguration = reportDataConfiguration.copy(
                    reports = ReportsConfiguration().withInstrumentInterestRate(enabled = true)
                )
            )
        )
        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON))).thenReturn(JavaBigDecimal.ONE)
        whenever(conversionRateService.getRate(any(), any<Instrument>(), eq(RON))).thenReturn(JavaBigDecimal("100.0"))

        val interval = ReportDataInterval.Monthly(YearMonth(2021, 1), YearMonth(2021, 3), null)
        mockTransactions(
            interval, investmentReportView.fundId, listOf(
                openPositionTransaction(
                    userId, LocalDate(2021, 2, 15),
                    currencyRecord = record(investmentReportView.fundId, cashAccountId, BigDecimal.parseString("-100.0"), RON, labelsOf("need")),
                    instrumentRecord = record(investmentReportView.fundId, cashAccountId, BigDecimal.parseString("1.0"), Instrument("AAPL"), labelsOf("need"))
                )
            )
        )

        val response = httpClient.get("/funds-api/reporting/v1/report-views/${investmentReportView.id}/data/unit-interest-rate") {
            header(USER_ID_HEADER, userId.toString())
            parameter("granularity", TimeGranularityTO.MONTHLY.name)
            parameter("fromYearMonth", "2021-01")
            parameter("toYearMonth", "2021-03")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val reportData = response.body<ReportDataTO<InstrumentsInterestRateReportTO>>()
        assertThat(reportData.viewId).isEqualTo(investmentReportView.id)
        assertThat(reportData.timeBuckets).hasSize(3)
        assertThat(reportData.timeBuckets[1].report.reports).isNotEmpty()
        assertThat(reportData.timeBuckets[1].report.reports[0].instrument).isEqualTo(Instrument("AAPL"))
        assertThat(reportData.timeBuckets[1].report.reports[0].totalInterestRate).isNotNull()
        assertThat(reportData.timeBuckets[1].report.reports[0].currentInterestRate).isNotNull()
    }

    @Test
    fun `get performance data report`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()
        val investmentReportView = reportViewRepository.save(
            reportViewCommand.copy(
                dataConfiguration = reportDataConfiguration.copy(
                    reports = ReportsConfiguration().withPerformanceReport(enabled = true)
                )
            )
        )
        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON))).thenReturn(JavaBigDecimal.ONE)
        whenever(conversionRateService.getRate(any(), any<Instrument>(), eq(RON))).thenReturn(JavaBigDecimal("100.0"))

        val interval = ReportDataInterval.Monthly(YearMonth(2021, 1), YearMonth(2021, 3), null)
        mockTransactions(
            interval, investmentReportView.fundId, listOf(
                openPositionTransaction(
                    userId, LocalDate(2021, 2, 15),
                    currencyRecord = record(investmentReportView.fundId, cashAccountId, BigDecimal.parseString("-100.0"), RON, labelsOf("need")),
                    instrumentRecord = record(investmentReportView.fundId, cashAccountId, BigDecimal.parseString("1.0"), Instrument("AAPL"), labelsOf("need"))
                )
            )
        )

        val response = httpClient.get("/funds-api/reporting/v1/report-views/${investmentReportView.id}/data/performance") {
            header(USER_ID_HEADER, userId.toString())
            parameter("granularity", TimeGranularityTO.MONTHLY.name)
            parameter("fromYearMonth", "2021-01")
            parameter("toYearMonth", "2021-03")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val reportData = response.body<ReportDataTO<PerformanceReportTO>>()
        assertThat(reportData.viewId).isEqualTo(investmentReportView.id)
        assertThat(reportData.timeBuckets).hasSize(3)
        assertThat(reportData.timeBuckets[0].report.totalAssetsValue).isNotNull()
        assertThat(reportData.timeBuckets[0].report.totalInvestment).isNotNull()
        assertThat(reportData.timeBuckets[0].report.totalProfit).isNotNull()
    }

    @Test
    fun `get unit performance data report`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()
        val investmentReportView = reportViewRepository.save(
            reportViewCommand.copy(
                dataConfiguration = reportDataConfiguration.copy(
                    reports = ReportsConfiguration().withInstrumentPerformanceReport(enabled = true)
                )
            )
        )
        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON))).thenReturn(JavaBigDecimal.ONE)
        whenever(conversionRateService.getRate(any(), any<Instrument>(), eq(RON))).thenReturn(JavaBigDecimal("100.0"))

        val interval = ReportDataInterval.Monthly(YearMonth(2021, 1), YearMonth(2021, 3), null)
        mockTransactions(
            interval, investmentReportView.fundId, listOf(
                openPositionTransaction(
                    userId, LocalDate(2021, 2, 15),
                    currencyRecord = record(investmentReportView.fundId, cashAccountId, BigDecimal.parseString("-100.0"), RON, labelsOf("need")),
                    instrumentRecord = record(investmentReportView.fundId, cashAccountId, BigDecimal.parseString("1.0"), Instrument("AAPL"), labelsOf("need"))
                )
            )
        )

        val response = httpClient.get("/funds-api/reporting/v1/report-views/${investmentReportView.id}/data/unit-performance") {
            header(USER_ID_HEADER, userId.toString())
            parameter("granularity", TimeGranularityTO.MONTHLY.name)
            parameter("fromYearMonth", "2021-01")
            parameter("toYearMonth", "2021-03")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val reportData = response.body<ReportDataTO<InstrumentsPerformanceReportTO>>()
        assertThat(reportData.viewId).isEqualTo(investmentReportView.id)
        assertThat(reportData.timeBuckets).hasSize(3)
        assertThat(reportData.timeBuckets[1].report.reports).isNotEmpty()
        assertThat(reportData.timeBuckets[1].report.reports[0].instrument).isEqualTo(Instrument("AAPL"))
        assertThat(reportData.timeBuckets[1].report.reports[0].totalUnits).isNotNull()
        assertThat(reportData.timeBuckets[1].report.reports[0].totalValue).isNotNull()
        assertThat(reportData.timeBuckets[1].report.reports[0].totalProfit).isNotNull()
    }

    @Test
    fun `get unit performance data report when feature is disabled should return error`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()
        val investmentReportView = reportViewRepository.save(
            reportViewCommand.copy(
                dataConfiguration = reportDataConfiguration.copy(
                    reports = ReportsConfiguration().withInstrumentPerformanceReport(enabled = false)
                )
            )
        )

        val response = httpClient.get("/funds-api/reporting/v1/report-views/${investmentReportView.id}/data/unit-performance") {
            header(USER_ID_HEADER, userId.toString())
            parameter("granularity", TimeGranularityTO.MONTHLY.name)
            parameter("fromYearMonth", "2021-01")
            parameter("toYearMonth", "2021-03")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    private suspend fun mockTransactions(
        interval: ReportDataInterval,
        fundId: Uuid,
        transactions: List<TransactionTO>,
    ) {
        whenever(
            transactionSdk.listTransactions(
                userId,
                TransactionFilterTO(null, interval.getPreviousLastDay(), fundId)
            )
        ).thenReturn(ListTO(transactions.filter { it.dateTime.date <= interval.getPreviousLastDay() }))
        interval.getBuckets().forEach { bucket ->
            whenever(
                transactionSdk.listTransactions(
                    userId,
                    TransactionFilterTO(bucket.from, bucket.to, fundId)
                )
            ).thenReturn(ListTO(transactions.filter { it.dateTime.date >= bucket.from && it.dateTime.date <= bucket.to }))
        }
    }

    fun singleRecordTransaction(
        userId: Uuid,
        date: LocalDate,
        record: TransactionRecordTO.CurrencyRecord,
    ): TransactionTO.SingleRecord =
        TransactionTO.SingleRecord(uuid4(), userId, uuid4().toString(), date.atTime(12, 0), record)

    fun openPositionTransaction(
        userId: Uuid,
        date: LocalDate,
        currencyRecord: TransactionRecordTO.CurrencyRecord,
        instrumentRecord: TransactionRecordTO.InstrumentRecord,
    ): TransactionTO.OpenPosition =
        TransactionTO.OpenPosition(uuid4(), userId, uuid4().toString(), date.atTime(12, 0), currencyRecord, instrumentRecord)

    fun record(
        fundId: Uuid,
        accountId: Uuid,
        amount: BigDecimal,
        currency: Currency,
        labels: List<Label>,
    ): TransactionRecordTO.CurrencyRecord =
        TransactionRecordTO.CurrencyRecord(uuid4(), accountId, fundId, amount, currency, labels)

    fun record(
        fundId: Uuid,
        accountId: Uuid,
        amount: BigDecimal,
        instrument: Instrument,
        labels: List<Label>,
    ): TransactionRecordTO.InstrumentRecord =
        TransactionRecordTO.InstrumentRecord(uuid4(), accountId, fundId, amount, instrument, labels)

    private fun Application.testModule() {
        val importAppTestModule = module {
            single<TransactionSdk> { transactionSdk }
            single<ConversionRateService> { conversionRateService }
        }
        configureDependencies(reportingDependencies, importAppTestModule)
        configureReportingErrorHandling()
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureReportingRouting()
    }
}
