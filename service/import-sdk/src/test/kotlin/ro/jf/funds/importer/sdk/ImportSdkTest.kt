package ro.jf.funds.importer.sdk

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.MediaType
import ro.jf.bk.commons.model.ResponseTO
import ro.jf.bk.commons.test.extension.MockServerExtension
import ro.jf.bk.commons.web.USER_ID_HEADER
import ro.jf.funds.importer.api.model.AccountMatcherTO
import ro.jf.funds.importer.api.model.FundMatcherTO
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import java.io.File
import java.util.UUID.randomUUID

@ExtendWith(MockServerExtension::class)
class ImportSdkTest {
    private val importSdk = ImportSdk(baseUrl = MockServerExtension.baseUrl)

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
                AccountMatcherTO("Cash RON", "Cash")
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByAccount("Cash RON", "Expenses")
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
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody(
                        buildJsonObject {
                            put("response", JsonPrimitive("success"))
                        }.toString()
                    )
            )

        val response = importSdk.import(
            userId = userId,
            importConfiguration = importConfiguration,
            csvFiles = files
        )

        assertThat(response).isNotNull
        assertThat(response is ResponseTO.Success).isTrue()
        val importResponse = (response as ResponseTO.Success).value
        assertThat(importResponse.response).isEqualTo("success")
    }

    @Test
    fun `given import failed due to client error should retrieve response`(mockServerClient: MockServerClient): Unit = runBlocking {
        val userId = randomUUID()
        val files = listOf(
            File("src/test/resources/mock/import-file-1.csv"),
            File("src/test/resources/mock/import-file-2.csv"),
        )
        val importConfiguration = ImportConfigurationTO(
            fileType = ImportFileTypeTO.WALLET_CSV,
            accountMatchers = listOf(
                AccountMatcherTO("Cash RON", "Cash")
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByAccount("Cash RON", "Expenses")
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

        val response = importSdk.import(
            userId = userId,
            importConfiguration = importConfiguration,
            csvFiles = files
        )

        assertThat(response).isNotNull
        assertThat(response is ResponseTO.Problem).isTrue()
        val importResponse = (response as ResponseTO.Problem).value
        assertThat(importResponse.title).isEqualTo("Problem title")
        assertThat(importResponse.detail).isEqualTo("Specific problem detail")
    }
}
