package ro.jf.funds.reporting.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ro.jf.funds.commons.test.utils.configureEnvironment
import ro.jf.funds.commons.test.utils.configureEnvironmentWithDB
import ro.jf.funds.commons.test.utils.createJsonHttpClient
import ro.jf.funds.commons.test.utils.dbConfig
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.CreateReportViewTaskTO
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

        val response = httpClient.post("/bk-api/reporting/v1/report-views") {
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
        val createReportViewTaskTO = response.body<CreateReportViewTaskTO>()
        assertThat(createReportViewTaskTO.taskId).isNotNull
        assertThat(createReportViewTaskTO).isInstanceOf(CreateReportViewTaskTO.Completed::class.java)
        createReportViewTaskTO as CreateReportViewTaskTO.Completed
        assertThat(createReportViewTaskTO.report.name).isEqualTo(expenseReportName)
        assertThat(createReportViewTaskTO.report.fundId).isEqualTo(expenseFundId)
        assertThat(createReportViewTaskTO.report.type).isEqualTo(ReportViewTypeTO.EXPENSE)
    }
}