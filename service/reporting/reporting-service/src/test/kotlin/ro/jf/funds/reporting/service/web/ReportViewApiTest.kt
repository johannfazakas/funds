package ro.jf.funds.reporting.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import ro.jf.funds.commons.event.ConsumerProperties
import ro.jf.funds.commons.event.asEvent
import ro.jf.funds.commons.event.createKafkaConsumer
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.*
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.reporting.api.event.REPORTING_DOMAIN
import ro.jf.funds.reporting.api.event.REPORT_VIEW_REQUEST
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTaskTO
import ro.jf.funds.reporting.api.model.ReportViewType
import ro.jf.funds.reporting.service.domain.ReportView
import ro.jf.funds.reporting.service.domain.ReportViewTask
import ro.jf.funds.reporting.service.module
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.persistence.ReportViewTaskRepository
import java.time.Duration.ofSeconds
import java.util.UUID.randomUUID

@ExtendWith(KafkaContainerExtension::class)
@ExtendWith(PostgresContainerExtension::class)
class ReportViewApiTest {
    private val createReportViewTopic = testTopicSupplier.topic(REPORTING_DOMAIN, REPORT_VIEW_REQUEST)
    private val consumerProperties = ConsumerProperties(KafkaContainerExtension.bootstrapServers, "test-consumer")
    private val createReportViewRepository = ReportViewRepository(PostgresContainerExtension.connection)
    private val createReportViewTaskRepository = ReportViewTaskRepository(PostgresContainerExtension.connection)

    private val userId = randomUUID()
    private val expenseFundId = randomUUID()
    private val expenseReportName = "Expense Report"

    @Test
    fun `given create report view should create it async`() = testApplication {
        configureEnvironment({ module() }, dbConfig, kafkaConfig)

        val httpClient = createJsonHttpClient()

        val response = httpClient.post("/funds-api/reporting/v1/report-views/tasks") {
            header(USER_ID_HEADER, userId.toString())
            contentType(ContentType.Application.Json)
            setBody(
                CreateReportViewTO(
                    name = expenseReportName,
                    fundId = expenseFundId,
                    type = ReportViewType.EXPENSE
                )
            )
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Accepted)
        val reportViewTaskTO = response.body<ReportViewTaskTO>()
        assertThat(reportViewTaskTO.taskId).isNotNull
        assertThat(reportViewTaskTO).isInstanceOf(ReportViewTaskTO.InProgress::class.java)
        reportViewTaskTO as ReportViewTaskTO.InProgress

        val createReportViewTOConsumer = createKafkaConsumer(consumerProperties)
        createReportViewTOConsumer.subscribe(listOf(createReportViewTopic.value))

        await().atMost(ofSeconds(5)).untilAsserted {
            val createReportViewTO = createReportViewTOConsumer.poll(ofSeconds(1))
                .map { it.asEvent<CreateReportViewTO>() }
                .firstOrNull { it.payload.fundId == expenseFundId }
            assertThat(createReportViewTO).isNotNull
        }

        await().atMost(ofSeconds(10)).untilAsserted {
            val createReportViewTask = runBlocking {
                createReportViewTaskRepository.findById(userId, reportViewTaskTO.taskId)
            }
            assertThat(createReportViewTask).isNotNull
            assertThat(createReportViewTask).isInstanceOf(ReportViewTask.Completed::class.java)
            createReportViewTask as ReportViewTask.Completed
            assertThat(createReportViewTask.reportViewId).isNotNull()

            val createReportView = runBlocking {
                createReportViewRepository.findById(userId, createReportViewTask.reportViewId)
            }
            assertThat(createReportView).isNotNull
            createReportView as ReportView
            assertThat(createReportView.name).isEqualTo(expenseReportName)
            assertThat(createReportView.fundId).isEqualTo(expenseFundId)
            assertThat(createReportView.type).isEqualTo(ReportViewType.EXPENSE)
        }
    }

    @Test
    fun `given get report view task`() = testApplication {
        configureEnvironment({ module() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()
        val reportViewTask = createReportViewTaskRepository.create(userId)
        val reportView =
            createReportViewRepository.create(userId, expenseReportName, expenseFundId, ReportViewType.EXPENSE)
        createReportViewTaskRepository.complete(userId, reportViewTask.taskId, reportView.id)

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
        assertThat(reportViewTaskTO.report.type).isEqualTo(ReportViewType.EXPENSE)
    }

    @Test
    fun `given get report view`() = testApplication {
        configureEnvironment({ module() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()
        val reportView =
            createReportViewRepository.create(userId, expenseReportName, expenseFundId, ReportViewType.EXPENSE)

        val response = httpClient.get("/funds-api/reporting/v1/report-views/${reportView.id}") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val reportViewTO = response.body<ReportViewTO>()
        assertThat(reportViewTO).isNotNull
        assertThat(reportViewTO.id).isEqualTo(reportView.id)
        assertThat(reportViewTO.fundId).isEqualTo(expenseFundId)
        assertThat(reportViewTO.name).isEqualTo(expenseReportName)
        assertThat(reportViewTO.type).isEqualTo(ReportViewType.EXPENSE)
    }

    @Test
    fun `given list report views`() = testApplication {
        configureEnvironment({ module() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()
        val reportView =
            createReportViewRepository.create(userId, expenseReportName, expenseFundId, ReportViewType.EXPENSE)

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
        assertThat(reportViewTO.type).isEqualTo(ReportViewType.EXPENSE)
    }
}
