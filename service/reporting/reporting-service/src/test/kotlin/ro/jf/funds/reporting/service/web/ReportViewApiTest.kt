package ro.jf.funds.reporting.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import ro.jf.funds.commons.event.ConsumerProperties
import ro.jf.funds.commons.event.asEvent
import ro.jf.funds.commons.event.createKafkaConsumer
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.utils.*
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.reporting.api.event.REPORTING_DOMAIN
import ro.jf.funds.reporting.api.event.REPORT_VIEW_REQUEST
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTaskTO
import ro.jf.funds.reporting.api.model.ReportViewTypeTO
import ro.jf.funds.reporting.service.module
import java.time.Duration.ofSeconds
import java.util.UUID.randomUUID

@ExtendWith(KafkaContainerExtension::class)
class ReportViewApiTest {
    private val createReportViewTopic = testTopicSupplier.topic(REPORTING_DOMAIN, REPORT_VIEW_REQUEST)
    private val consumerProperties = ConsumerProperties(KafkaContainerExtension.bootstrapServers, "test-consumer")

    private val userId = randomUUID()
    private val expenseFundId = randomUUID()
    private val expenseReportName = "Expense Report"

    @Test
    fun `given create report view`() = testApplication {
        configureEnvironment({ module() }, dbConfig, kafkaConfig)

        val httpClient = createJsonHttpClient()

        val response = httpClient.post("/funds-api/reporting/v1/report-views/tasks") {
            header(USER_ID_HEADER, userId.toString())
            contentType(ContentType.Application.Json)
            setBody(
                CreateReportViewTO(
                    name = expenseReportName,
                    fundId = expenseFundId,
                    type = ReportViewTypeTO.EXPENSE
                )
            )
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Accepted)
        val reportViewTaskTO = response.body<ReportViewTaskTO>()
        assertThat(reportViewTaskTO.taskId).isNotNull
        assertThat(reportViewTaskTO).isInstanceOf(ReportViewTaskTO.Completed::class.java)
        reportViewTaskTO as ReportViewTaskTO.Completed
        assertThat(reportViewTaskTO.report.name).isEqualTo(expenseReportName)
        assertThat(reportViewTaskTO.report.fundId).isEqualTo(expenseFundId)
        assertThat(reportViewTaskTO.report.type).isEqualTo(ReportViewTypeTO.EXPENSE)

        val createReportViewTOConsumer = createKafkaConsumer(consumerProperties)
        createReportViewTOConsumer.subscribe(listOf(createReportViewTopic.value))

        await().atMost(ofSeconds(10)).untilAsserted {
            val createReportViewTO = createReportViewTOConsumer.poll(ofSeconds(1))
                .map { it.asEvent<CreateReportViewTO>() }
                .firstOrNull { it.payload.fundId == expenseFundId }
            assertThat(createReportViewTO).isNotNull
        }
    }
}
