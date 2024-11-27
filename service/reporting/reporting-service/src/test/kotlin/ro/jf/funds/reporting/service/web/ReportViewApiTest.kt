package ro.jf.funds.reporting.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ro.jf.funds.commons.test.utils.configureEnvironment
import ro.jf.funds.commons.test.utils.createJsonHttpClient
import ro.jf.funds.commons.test.utils.dbConfig
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTaskTO
import ro.jf.funds.reporting.api.model.ReportViewTypeTO
import ro.jf.funds.reporting.service.module
import java.util.UUID.randomUUID

class ReportViewApiTest {

    private val userId = randomUUID()
    private val expenseFundId = randomUUID()
    private val expenseReportName = "Expense Report"

    @Test
    fun `given create report view`() = testApplication {
        configureEnvironment({ module() }, dbConfig)

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
    }
}