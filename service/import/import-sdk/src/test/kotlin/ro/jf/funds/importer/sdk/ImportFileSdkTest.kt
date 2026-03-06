package ro.jf.funds.importer.sdk

import com.benasher44.uuid.uuid4
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
import ro.jf.funds.importer.api.model.*
import ro.jf.funds.platform.jvm.error.ApiException
import ro.jf.funds.platform.jvm.test.extension.MockServerContainerExtension
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER

@ExtendWith(MockServerContainerExtension::class)
class ImportFileSdkTest {
    private val importFileSdk = ImportFileSdk(baseUrl = MockServerContainerExtension.baseUrl)

    @Test
    fun `given create import file request should return response`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = uuid4()
        val importFileId = uuid4()
        mockServerClient
            .`when`(
                request()
                    .withMethod("POST")
                    .withPath("/funds-api/import/v1/import-files")
                    .withHeader(USER_ID_HEADER, userId.toString())
            )
            .respond(
                response()
                    .withStatusCode(201)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("importFileId", JsonPrimitive(importFileId.toString()))
                            put("fileName", JsonPrimitive("test.csv"))
                            put("type", JsonPrimitive("WALLET_CSV"))
                            put("status", JsonPrimitive("PENDING"))
                            put("uploadUrl", JsonPrimitive("http://localhost:4566/bucket/test.csv"))
                        }.toString()
                    )
            )

        val response = importFileSdk.createImportFile(
            userId,
            CreateImportFileRequest(
                fileName = "test.csv",
                type = ImportFileTypeTO.WALLET_CSV,
                importConfigurationId = uuid4(),
            )
        )

        assertThat(response.importFileId).isEqualTo(importFileId)
        assertThat(response.uploadUrl).isEqualTo("http://localhost:4566/bucket/test.csv")
    }

    @Test
    fun `given get import file request should return file details`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = uuid4()
        val importFileId = uuid4()
        val configId = uuid4()
        mockServerClient
            .`when`(
                request()
                    .withMethod("GET")
                    .withPath("/funds-api/import/v1/import-files/$importFileId")
                    .withHeader(USER_ID_HEADER, userId.toString())
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("importFileId", JsonPrimitive(importFileId.toString()))
                            put("fileName", JsonPrimitive("test.csv"))
                            put("type", JsonPrimitive("WALLET_CSV"))
                            put("status", JsonPrimitive("IMPORTED"))
                            put("importConfigurationId", JsonPrimitive(configId.toString()))
                            put("createdAt", JsonPrimitive("2026-01-01T00:00:00"))
                            put("updatedAt", JsonPrimitive("2026-01-01T00:00:00"))
                        }.toString()
                    )
            )

        val response = importFileSdk.getImportFile(userId, importFileId)

        assertThat(response.importFileId).isEqualTo(importFileId)
        assertThat(response.fileName).isEqualTo("test.csv")
        assertThat(response.status).isEqualTo(ImportFileStatusTO.IMPORTED)
    }

    @Test
    fun `given create import file request fails should throw api exception`(mockServerClient: MockServerClient): Unit =
        runBlocking {
            val userId = uuid4()
            mockServerClient
                .`when`(
                    request()
                        .withMethod("POST")
                        .withPath("/funds-api/import/v1/import-files")
                        .withHeader(USER_ID_HEADER, userId.toString())
                )
                .respond(
                    response()
                        .withStatusCode(400)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody(
                            buildJsonObject {
                                put("title", JsonPrimitive("Bad Request"))
                                put("detail", JsonPrimitive("Invalid request"))
                            }.toString()
                        )
                )

            assertThatThrownBy {
                runBlocking {
                    importFileSdk.createImportFile(
                        userId,
                        CreateImportFileRequest("test.csv", ImportFileTypeTO.WALLET_CSV, uuid4())
                    )
                }
            }
                .isInstanceOf(ApiException::class.java)
                .extracting("statusCode").isEqualTo(400)
        }
}
