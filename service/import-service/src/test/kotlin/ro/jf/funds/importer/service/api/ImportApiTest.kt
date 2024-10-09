package ro.jf.funds.importer.service.api

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ro.jf.bk.commons.web.USER_ID_HEADER
import ro.jf.funds.importer.api.model.*
import ro.jf.funds.importer.service.module
import java.io.File
import java.util.UUID.randomUUID

class ImportApiTest {
    @Test
    fun `test import`() = testApplication {
        configureEnvironment()

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val csvFile = File("src/test/resources/data/wallet_export.csv")
        val importConfiguration = ImportConfigurationTO(
            fileType = ImportFileTypeTO.WALLET_CSV,
            accountMatchers = listOf(
                AccountMatcherTO("ING old", "ING")
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByLabel("Basic - Food", "Expenses"),
                FundMatcherTO.ByLabel("Gifts", "Expenses"),
                FundMatcherTO.ByLabel("C&T - Gas & Parking", "Expenses"),
                FundMatcherTO.ByLabelAndAccountWithImplicitTransfer("ING Old", "Work Income", "Work", "Expenses"),
            )
        )

        val response = httpClient.post("/bk-api/import/v1/imports") {
            header(USER_ID_HEADER, userId.toString())
            setBody(MultiPartFormDataContent(
                formData {
                    append("file", csvFile.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Text.CSV)
                        append(HttpHeaders.ContentDisposition, "filename=\"${csvFile.name}\"")
                    })
                    append("configuration", Json.encodeToString(importConfiguration), Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                    })
                }
            ))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val responseBody = response.body<ImportResponse>()
        assertThat(responseBody.response).isNotEmpty()
    }

    private fun ApplicationTestBuilder.configureEnvironment() {
        environment {
            config = MapApplicationConfig()
        }
        application {
            module()
        }
    }

    private fun ApplicationTestBuilder.createJsonHttpClient() =
        createClient { install(ContentNegotiation) { json() } }
}
