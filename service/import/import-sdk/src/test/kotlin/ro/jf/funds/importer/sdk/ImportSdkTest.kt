package ro.jf.funds.importer.sdk

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.MediaType
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.commons.error.ApiException
import ro.jf.funds.commons.test.extension.MockServerContainerExtension
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.importer.api.model.*
import java.io.File
import java.util.UUID.randomUUID

@ExtendWith(MockServerContainerExtension::class)
class ImportSdkTest {
    private val importSdk = ImportSdk(baseUrl = MockServerContainerExtension.baseUrl)
    private val importTaskId = randomUUID()

    @Test
    fun `given import successful should retrieve response`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val files = listOf(
            File("src/test/resources/mock/import-file-1.csv"),
            File("src/test/resources/mock/import-file-2.csv"),
        )
        val importConfiguration = ImportConfigurationTO(
            fileType = ImportFileTypeTO.WALLET_CSV,
            accountMatchers = listOf(
                AccountMatcherTO("Cash RON", AccountName("Cash"))
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByAccount("Cash RON", FundName("Expenses"))
            )
        )
        mockServerClient
            .`when`(
                request()
                    .withMethod("POST")
                    .withPath("/bk-api/import/v1/imports")
                    .withHeader("Content-Type", "multipart/form-data.*")
                    .withHeader(USER_ID_HEADER, userId.toString())
            )
            .respond(
                response()
                    .withStatusCode(201)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("taskId", JsonPrimitive(importTaskId.toString()))
                            put("status", JsonPrimitive(ImportTaskTO.Status.IN_PROGRESS.name))
                        }.toString()
                    )
            )

        val response = importSdk.import(
            userId = userId,
            importConfiguration = importConfiguration,
            csvFiles = files
        )

        assertThat(response).isNotNull
        assertThat(response.taskId).isEqualTo(importTaskId)
        assertThat(response.status).isEqualTo(ImportTaskTO.Status.IN_PROGRESS)
    }

    @Test
    fun `given import failed due to client format error should retrieve response`(mockServerClient: MockServerClient): Unit =
        runBlocking {
            val userId = randomUUID()
            val files = listOf(
                File("src/test/resources/mock/import-file-1.csv"),
                File("src/test/resources/mock/import-file-2.csv"),
            )
            val importConfiguration = ImportConfigurationTO(
                fileType = ImportFileTypeTO.WALLET_CSV,
                accountMatchers = listOf(
                    AccountMatcherTO("Cash RON", AccountName("Cash"))
                ),
                fundMatchers = listOf(
                    FundMatcherTO.ByAccount("Cash RON", FundName("Expenses"))
                )
            )
            mockServerClient
                .`when`(
                    request()
                        .withMethod("POST")
                        .withPath("/bk-api/import/v1/imports")
                        .withHeader("Content-Type", "multipart/form-data.*")
                        .withHeader(USER_ID_HEADER, userId.toString())
                )
                .respond(
                    response()
                        .withStatusCode(400)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody(
                            buildJsonObject {
                                put("title", JsonPrimitive("Problem title"))
                                put("detail", JsonPrimitive("Specific problem detail"))
                            }.toString()
                        )
                )

            assertThatThrownBy { runBlocking { importSdk.import(userId, importConfiguration, files) } }
                .isInstanceOf(ApiException::class.java)
                .extracting("statusCode").isEqualTo(400)
        }

    @Test
    fun `given import failed due to client data error should retrieve response`(mockServerClient: MockServerClient): Unit =
        runBlocking {
            val userId = randomUUID()
            val files = listOf(
                File("src/test/resources/mock/import-file-1.csv"),
                File("src/test/resources/mock/import-file-2.csv"),
            )
            val importConfiguration = ImportConfigurationTO(
                fileType = ImportFileTypeTO.WALLET_CSV,
                accountMatchers = listOf(
                    AccountMatcherTO("Cash RON", AccountName("Cash"))
                ),
                fundMatchers = listOf(
                    FundMatcherTO.ByAccount("Cash RON", FundName("Expenses"))
                )
            )
            mockServerClient
                .`when`(
                    request()
                        .withMethod("POST")
                        .withPath("/bk-api/import/v1/imports")
                        .withHeader("Content-Type", "multipart/form-data.*")
                        .withHeader(USER_ID_HEADER, userId.toString())
                )
                .respond(
                    response()
                        .withStatusCode(422)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody(
                            buildJsonObject {
                                put("title", JsonPrimitive("Problem title"))
                                put("detail", JsonPrimitive("Specific problem detail"))
                            }.toString()
                        )
                )

            assertThatThrownBy { runBlocking { importSdk.import(userId, importConfiguration, files) } }
                .isInstanceOf(ApiException::class.java)
                .extracting("statusCode").isEqualTo(422)
        }
}
