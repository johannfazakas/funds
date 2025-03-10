package ro.jf.funds.reporting.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.event.ConsumerProperties
import ro.jf.funds.commons.event.asEvent
import ro.jf.funds.commons.event.createKafkaConsumer
import ro.jf.funds.commons.model.Currency.Companion.EUR
import ro.jf.funds.commons.model.Currency.Companion.RON
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.model.labelsOf
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.*
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.reporting.api.event.REPORTING_DOMAIN
import ro.jf.funds.reporting.api.event.REPORT_VIEW_REQUEST
import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.service.config.configureReportingErrorHandling
import ro.jf.funds.reporting.service.config.configureReportingEventHandling
import ro.jf.funds.reporting.service.config.configureReportingRouting
import ro.jf.funds.reporting.service.config.reportingDependencies
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.persistence.ReportViewTaskRepository
import ro.jf.funds.reporting.service.utils.record
import ro.jf.funds.reporting.service.utils.transaction
import java.math.BigDecimal
import java.time.Duration.ofSeconds
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(KafkaContainerExtension::class)
@ExtendWith(PostgresContainerExtension::class)
class ReportingApiTest {
    private val createReportViewTopic = testTopicSupplier.topic(REPORTING_DOMAIN, REPORT_VIEW_REQUEST)
    private val consumerProperties = ConsumerProperties(KafkaContainerExtension.bootstrapServers, "test-consumer")
    private val reportViewRepository = ReportViewRepository(PostgresContainerExtension.connection)
    private val reportViewTaskRepository = ReportViewTaskRepository(PostgresContainerExtension.connection)
    private val reportRecordRepository = ReportRecordRepository(PostgresContainerExtension.connection)
    private val fundTransactionSdk = mock<FundTransactionSdk>()
    private val historicalPricingSdk = mock<HistoricalPricingSdk>()

    private val userId = randomUUID()
    private val expenseFundId = randomUUID()
    private val expenseReportName = "Expense Report"
    private val cashAccountId = randomUUID()
    private val dateTime = LocalDateTime.parse("2021-09-01T12:00:00")
    private val labels = labelsOf("need", "want")
    private val reportDataConfiguration = ReportDataConfiguration(
        currency = RON,
        filter = RecordFilter(labels),
        groups = null,
        features = ReportDataFeaturesConfiguration()
            .withNet(enabled = true, applyFilter = true)
            .withValueReport(enabled = true)
    )
    private val reportViewCommand = CreateReportViewCommand(
        userId = userId,
        name = expenseReportName,
        fundId = expenseFundId,
        dataConfiguration = reportDataConfiguration
    )

    @Test
    fun `create report view should create it async`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val httpClient = createJsonHttpClient()

        val transaction =
            transaction(
                userId, dateTime, listOf(
                    record(expenseFundId, cashAccountId, BigDecimal("-100.0"), RON, labelsOf("need"))
                )
            )
        whenever(fundTransactionSdk.listTransactions(userId, expenseFundId)).thenReturn(ListTO.of(transaction))

        val response = httpClient.post("/funds-api/reporting/v1/report-views/tasks") {
            header(USER_ID_HEADER, userId.toString())
            contentType(ContentType.Application.Json)
            setBody(
                CreateReportViewTO(
                    name = expenseReportName,
                    fundId = expenseFundId,
                    dataConfiguration = ReportDataConfigurationTO(
                        currency = RON,
                        filter = RecordFilterTO(labels),
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
                        features = ReportDataFeaturesConfigurationTO(
                            net = NetReportFeatureTO(enabled = true, applyFilter = true),
                            valueReport = GenericReportFeatureTO(true)
                        )
                    )
                )
            )
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Accepted)
        val reportViewTaskTO = response.body<ReportViewTaskTO>()
        assertThat(reportViewTaskTO.taskId).isNotNull
        assertThat(reportViewTaskTO).isInstanceOf(ReportViewTaskTO.InProgress::class.java)
        reportViewTaskTO as ReportViewTaskTO.InProgress

        val createReportViewCommandConsumer = createKafkaConsumer(consumerProperties)
        createReportViewCommandConsumer.subscribe(listOf(createReportViewTopic.value))

        await().atMost(ofSeconds(5)).untilAsserted {
            val createReportViewCommand = createReportViewCommandConsumer.poll(ofSeconds(1))
                .map { it.asEvent<CreateReportViewCommand>() }
                .firstOrNull { it.payload.fundId == expenseFundId }
            assertThat(createReportViewCommand).isNotNull
        }

        await().atMost(ofSeconds(10)).untilAsserted {
            val createReportViewTask = runBlocking {
                reportViewTaskRepository.findById(userId, reportViewTaskTO.taskId)
            }
            assertThat(createReportViewTask).isNotNull
            assertThat(createReportViewTask).isInstanceOf(ReportViewTask.Completed::class.java)
            createReportViewTask as ReportViewTask.Completed
            assertThat(createReportViewTask.reportViewId).isNotNull()

            val createReportView = runBlocking {
                reportViewRepository.findById(userId, createReportViewTask.reportViewId)
            }
            assertThat(createReportView).isNotNull
            createReportView as ReportView
            assertThat(createReportView.name).isEqualTo(expenseReportName)
            assertThat(createReportView.fundId).isEqualTo(expenseFundId)
            assertThat(createReportView.dataConfiguration.currency).isEqualTo(RON)
            assertThat(createReportView.dataConfiguration.filter.labels).containsExactlyElementsOf(labels)
            assertThat(createReportView.dataConfiguration.groups).hasSize(2)
            assertThat(createReportView.dataConfiguration.features.net.enabled).isTrue()
            assertThat(createReportView.dataConfiguration.features.net.applyFilter).isTrue()
        }
    }

    @Test
    fun `get report view task`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()
        val reportViewTask = reportViewTaskRepository.create(userId)
        val reportView =
            reportViewRepository.save(reportViewCommand)
        reportViewTaskRepository.complete(userId, reportViewTask.taskId, reportView.id)

        val response = httpClient.get("/funds-api/reporting/v1/report-views/tasks/${reportViewTask.taskId}") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val reportViewTaskTO = response.body<ReportViewTaskTO>()
        assertThat(reportViewTaskTO).isNotNull
        assertThat(reportViewTaskTO.taskId).isEqualTo(reportViewTask.taskId)
        assertThat(reportViewTaskTO).isInstanceOf(ReportViewTaskTO.Completed::class.java)
        reportViewTaskTO as ReportViewTaskTO.Completed
        assertThat(reportViewTaskTO.report.id).isEqualTo(reportView.id)
        assertThat(reportViewTaskTO.report.fundId).isEqualTo(expenseFundId)
        assertThat(reportViewTaskTO.report.name).isEqualTo(expenseReportName)
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
        whenever(conversions.getRate(eq(EUR), eq(RON), any())).thenReturn(BigDecimal("5.0"))
        whenever(historicalPricingSdk.convert(eq(userId), any())).thenReturn(conversions)
        reportRecordRepository.save(
            CreateReportRecordCommand(
                userId, reportView.id, randomUUID(), LocalDate(2021, 1, 2), RON,
                BigDecimal("-25.0"), BigDecimal("-25.0"), labelsOf("need")
            ),
        )
        reportRecordRepository.save(
            CreateReportRecordCommand(
                userId, reportView.id, randomUUID(), LocalDate.parse("2021-01-02"), EUR,
                BigDecimal("-10.0"), BigDecimal("-50.0"), labelsOf("want")
            )
        )

        val response = httpClient.get("/funds-api/reporting/v1/report-views/${reportView.id}/data") {
            header(USER_ID_HEADER, userId.toString())
            parameter("granularity", "DAILY")
            parameter("from", "2021-01-01")
            parameter("to", "2021-01-28")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val reportData = response.body<ReportDataTO>()
        assertThat(reportData.viewId).isEqualTo(reportView.id)
        assertThat(reportData.data).hasSize(28)
        assertThat(reportData.data[0])
            .isEqualTo(
                ReportDataItemTO(
                    timeBucket = DateInterval(LocalDate(2021, 1, 1), LocalDate(2021, 1, 1)),
                    net = BigDecimal("0.0"),
                    value = ValueReportTO(BigDecimal("0.0"), BigDecimal("0.0"), BigDecimal("0.0"), BigDecimal("0.0"))
                )
            )
        assertThat(reportData.data[1])
            .isEqualTo(
                ReportDataItemTO(
                    timeBucket = DateInterval(LocalDate(2021, 1, 2), LocalDate(2021, 1, 2)),
                    net = BigDecimal("-75.0"),
                    value = ValueReportTO(BigDecimal("0.0"), BigDecimal("-75.0"), BigDecimal("0.0"), BigDecimal("0.0"))
                )
            )
    }

    @Test
    fun `get report view without granularity`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()
        val reportView = reportViewRepository.save(reportViewCommand)
        val response = httpClient.get("/funds-api/reporting/v1/report-views/${reportView.id}/data") {
            header(USER_ID_HEADER, userId.toString())
            parameter("from", "2021-01-01")
            parameter("to", "2021-01-31")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    private fun Application.testModule() {
        val importAppTestModule = module {
            single<FundTransactionSdk> { fundTransactionSdk }
            single<HistoricalPricingSdk> { historicalPricingSdk }
        }
        configureDependencies(reportingDependencies, importAppTestModule)
        configureReportingErrorHandling()
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureReportingEventHandling()
        configureReportingRouting()
    }
}
