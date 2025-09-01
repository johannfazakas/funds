package ro.jf.funds.reporting.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
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
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.event.ConsumerProperties
import ro.jf.funds.commons.model.Currency.Companion.EUR
import ro.jf.funds.commons.model.Currency.Companion.RON
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.model.labelsOf
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.*
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.fund.api.model.FundTransactionFilterTO
import ro.jf.funds.fund.api.model.FundTransactionTO
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.reporting.api.event.REPORTING_DOMAIN
import ro.jf.funds.reporting.api.event.REPORT_VIEW_REQUEST
import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.service.config.configureReportingErrorHandling
import ro.jf.funds.reporting.service.config.configureReportingRouting
import ro.jf.funds.reporting.service.config.reportingDependencies
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.service.data.ConversionRateService
import ro.jf.funds.reporting.service.utils.record
import ro.jf.funds.reporting.service.utils.transaction
import java.math.BigDecimal
import java.util.*
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(KafkaContainerExtension::class)
@ExtendWith(PostgresContainerExtension::class)
class ReportingApiTest {
    private val createReportViewTopic = testTopicSupplier.topic(REPORTING_DOMAIN, REPORT_VIEW_REQUEST)
    private val consumerProperties = ConsumerProperties(KafkaContainerExtension.bootstrapServers, "test-consumer")
    private val reportViewRepository = ReportViewRepository(PostgresContainerExtension.connection)
    private val fundTransactionSdk = mock<FundTransactionSdk>()
    private val conversionRateService = mock<ConversionRateService>()

    private val userId = randomUUID()
    private val expenseFundId = randomUUID()
    private val expenseReportName = "Expense Report"
    private val cashAccountId = randomUUID()
    private val dateTime = LocalDateTime.parse("2021-09-01T12:00:00")
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
            transaction(
                userId, dateTime, listOf(
                    record(expenseFundId, cashAccountId, BigDecimal("-100.0"), RON, labelsOf("need"))
                )
            )
        whenever(fundTransactionSdk.listTransactions(userId, expenseFundId)).thenReturn(ListTO.of(transaction))

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
    fun `get report view data`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()
        val reportView = reportViewRepository.save(reportViewCommand)
        val conversions = mock<ConversionsResponse>()
        whenever(conversionRateService.getRate(eq(userId), any(), eq(EUR), eq(RON))).thenReturn(BigDecimal("5.0"))
        whenever(conversionRateService.getRate(eq(userId), any(), eq(RON), eq(RON))).thenReturn(BigDecimal.ONE)

        val fromDate = LocalDate(2021, 1, 1)
        val toDate = LocalDate(2021, 1, 28)
        val interval = ReportDataInterval.Daily(fromDate, toDate, null)
        mockTransactions(
            interval, reportView.fundId, listOf(
                transaction(
                    userId, LocalDate(2021, 1, 2).atTime(12, 0),
                    listOf(
                        record(
                            reportView.fundId, cashAccountId, BigDecimal("-25.0"), RON, labelsOf("need")
                        )
                    )
                ),
                transaction(
                    userId, LocalDate(2021, 1, 2).atTime(12, 0),
                    listOf(
                        record(
                            reportView.fundId, cashAccountId, BigDecimal("-10.0"), EUR, labelsOf("want")
                        )
                    )
                )
            )
        )

        val response = httpClient.get("/funds-api/reporting/v1/report-views/${reportView.id}/data") {
            header(USER_ID_HEADER, userId.toString())
            parameter("granularity", TimeGranularityTO.DAILY.name)
            parameter("fromDate", fromDate.toString())
            parameter("toDate", toDate.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val reportData = response.body<ReportDataTO<ReportDataAggregateTO>>()
        assertThat(reportData.viewId).isEqualTo(reportView.id)
        assertThat(reportData.data).hasSize(28)
        assertThat(reportData.data[0])
            .isEqualTo(
                ReportDataItemTO(
                    timeBucket = DateIntervalTO(LocalDate(2021, 1, 1), LocalDate(2021, 1, 1)),
                    bucketType = BucketTypeTO.REAL,
                    data = ReportDataAggregateTO(
                        net = BigDecimal("0.0"),
                        value = ValueReportItemTO(
                            BigDecimal("0.0"),
                            BigDecimal("0.0"),
                            BigDecimal("0.0"),
                            BigDecimal("0.0")
                        ),
                    )
                )
            )
        assertThat(reportData.data[1])
            .isEqualTo(
                ReportDataItemTO(
                    timeBucket = DateIntervalTO(LocalDate(2021, 1, 2), LocalDate(2021, 1, 2)),
                    bucketType = BucketTypeTO.REAL,
                    data = ReportDataAggregateTO(
                        net = BigDecimal("-75.0"),
                        value = ValueReportItemTO(
                            BigDecimal("0.0"),
                            BigDecimal("-75.0"),
                            BigDecimal("0.0"),
                            BigDecimal("0.0")
                        ),
                    )
                )
            )
    }

    private suspend fun mockTransactions(
        interval: ReportDataInterval,
        fundId: UUID,
        transactions: List<FundTransactionTO>,
    ) {
        whenever(
            fundTransactionSdk.listTransactions(
                userId,
                fundId,
                FundTransactionFilterTO(null, interval.getPreviousLastDay())
            )
        ).thenReturn(ListTO(transactions.filter { it.dateTime.date <= interval.getPreviousLastDay() }))
        interval.getBuckets().forEach { bucket ->
            whenever(
                fundTransactionSdk.listTransactions(
                    userId,
                    fundId,
                    FundTransactionFilterTO(bucket.from, bucket.to)
                )
            ).thenReturn(ListTO(transactions.filter { it.dateTime.date >= bucket.from && it.dateTime.date <= bucket.to }))
        }
    }

    private fun Application.testModule() {
        val importAppTestModule = module {
            single<FundTransactionSdk> { fundTransactionSdk }
            single<ConversionRateService> { conversionRateService }
        }
        configureDependencies(reportingDependencies, importAppTestModule)
        configureReportingErrorHandling()
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureReportingRouting()
    }
}
