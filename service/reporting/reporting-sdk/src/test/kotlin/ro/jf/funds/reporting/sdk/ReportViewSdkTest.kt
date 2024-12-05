package ro.jf.funds.reporting.sdk

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.MediaType
import ro.jf.funds.commons.test.extension.MockServerContainerExtension
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewTaskTO
import ro.jf.funds.reporting.api.model.ReportViewType
import java.util.UUID.randomUUID

@ExtendWith(MockServerContainerExtension::class)
class ReportViewSdkTest {
//    private val reportViewSdk = ReportViewSdk(baseUrl = MockServerContainerExtension.baseUrl)
//
//    val userId = randomUUID()
//    val taskId = randomUUID()
//    val fundId = randomUUID()
//
//    @Test
//    fun `given create report view task`(mockServerClient: MockServerClient): Unit = runBlocking {
//        mockServerClient
//            .`when`(
//                request()
//                    .withMethod("POST")
//                    .withPath("/bk-api/reporting/v1/report-views/tasks")
//                    .withHeader(USER_ID_HEADER, userId.toString())
//            )
//            .respond(
//                response()
//                    .withStatusCode(202)
//                    .withContentType(MediaType.APPLICATION_JSON)
//                    .withBody(
//                        buildJsonObject {
//                            put("taskId", JsonPrimitive(taskId.toString()))
//                            put("type", JsonPrimitive("IN_PROGRESS"))
//                        }.toString()
//                    )
//            )
//        val request = CreateReportViewTO(
//            name = "Expense Report",
//            type = ReportViewType.EXPENSE,
//            fundId = fundId
//        )
//
//        val response = reportViewSdk.createReportView(userId, request)
//
//        assertThat(response.taskId).isEqualTo(taskId)
//        assertThat(response).isInstanceOf(ReportViewTaskTO.InProgress::class.java)
//    }
//
//    @Test
//    fun `given get report view task`(mockServerClient: MockServerClient): Unit = runBlocking {
//        mockServerClient
//            .`when`(
//                request()
//                    .withMethod("GET")
//                    .withPath("/bk-api/reporting/v1/report-views/tasks/$taskId")
//                    .withHeader(USER_ID_HEADER, userId.toString())
//            )
//            .respond(
//                response()
//                    .withStatusCode(200)
//                    .withContentType(MediaType.APPLICATION_JSON)
//                    .withBody(
//                        buildJsonObject {
//                            put("taskId", JsonPrimitive(taskId.toString()))
//                            put("type", JsonPrimitive("IN_PROGRESS"))
//                        }.toString()
//                    )
//            )
//
//        val response = reportViewSdk.getReportViewTask(userId, taskId)
//
//        assertThat(response.taskId).isEqualTo(taskId)
//        assertThat(response).isInstanceOf(ReportViewTaskTO.InProgress::class.java)
//    }
//
//    @Test
//    fun `given get report view`(mockServerClient: MockServerClient): Unit = runBlocking {
//        mockServerClient
//            .`when`(
//                request()
//                    .withMethod("GET")
//                    .withPath("/bk-api/reporting/v1/report-views/$fundId")
//                    .withHeader(USER_ID_HEADER, userId.toString())
//            )
//            .respond(
//                response()
//                    .withStatusCode(200)
//                    .withContentType(MediaType.APPLICATION_JSON)
//                    .withBody(
//                        buildJsonObject {
//                            put("id", JsonPrimitive(fundId.toString()))
//                            put("name", JsonPrimitive("Expense Report"))
//                            put("fundId", JsonPrimitive(fundId.toString()))
//                            put("type", JsonPrimitive("EXPENSE"))
//                        }.toString()
//                    )
//            )
//
//        val response = reportViewSdk.getReportView(userId, fundId)
//
//        assertThat(response.id).isEqualTo(fundId)
//        assertThat(response.name).isEqualTo("Expense Report")
//        assertThat(response.type).isEqualTo(ReportViewType.EXPENSE)
//    }
//
//    @Test
//    fun `given get report view list`(mockServerClient: MockServerClient): Unit = runBlocking {
//        mockServerClient
//            .`when`(
//                request()
//                    .withMethod("GET")
//                    .withPath("/bk-api/reporting/v1/report-views")
//                    .withHeader(USER_ID_HEADER, userId.toString())
//            )
//            .respond(
//                response()
//                    .withStatusCode(200)
//                    .withContentType(MediaType.APPLICATION_JSON)
//                    .withBody(
//                        buildJsonObject {
//                            put("items", buildJsonArray {
//                                add(
//                                    buildJsonObject {
//                                        put("id", JsonPrimitive(fundId.toString()))
//                                        put("name", JsonPrimitive("Expense Report"))
//                                        put("fundId", JsonPrimitive(fundId.toString()))
//                                        put("type", JsonPrimitive("EXPENSE"))
//                                    }
//                                )
//                            })
//                        }.toString()
//                    )
//            )
//
//        val response = reportViewSdk.listReportsViews(userId)
//
//        assertThat(response.items).hasSize(1)
//        assertThat(response.items[0].id).isEqualTo(fundId)
//        assertThat(response.items[0].name).isEqualTo("Expense Report")
//        assertThat(response.items[0].type).isEqualTo(ReportViewType.EXPENSE)
//    }
}