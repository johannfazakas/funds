package ro.jf.funds.importer.service.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ro.jf.bk.commons.model.ProblemTO
import ro.jf.bk.commons.test.utils.configureEnvironmentWithDB
import ro.jf.bk.commons.test.utils.createJsonHttpClient
import ro.jf.bk.commons.web.USER_ID_HEADER
import ro.jf.funds.importer.api.model.*
import ro.jf.funds.importer.service.module
import java.io.File
import java.util.UUID.randomUUID

class ImportApiTest {
    @Test
    fun `test valid import`() = testApplication {
        configureEnvironmentWithDB { module() }

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
                FundMatcherTO.ByAccountLabelWithTransfer("ING old", "Work Income", "Work", "Expenses"),
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

    @Test
    fun `test invalid import with missing configuration`(): Unit = testApplication {
        configureEnvironmentWithDB { module() }

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val csvFile = File("src/test/resources/data/wallet_export.csv")

        val response = httpClient.post("/bk-api/import/v1/imports") {
            header(USER_ID_HEADER, userId.toString())
            setBody(MultiPartFormDataContent(
                formData {
                    append("file", csvFile.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Text.CSV)
                        append(HttpHeaders.ContentDisposition, "filename=\"${csvFile.name}\"")
                    })
                }
            ))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val responseBody = response.body<ProblemTO>()
        assertThat(responseBody.title).isEqualTo("Import configuration missing.")
    }

    @Test
    fun `test invalid import with bad csv file`(): Unit = testApplication {
        configureEnvironmentWithDB { module() }

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val csvFile = File("src/test/resources/data/invalid_export.csv")
        val importConfiguration = ImportConfigurationTO(
            fileType = ImportFileTypeTO.WALLET_CSV,
            accountMatchers = listOf(
                AccountMatcherTO("ING old", "ING")
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByLabel("Basic - Food", "Expenses"),
                FundMatcherTO.ByLabel("Gifts", "Expenses"),
                FundMatcherTO.ByLabel("C&T - Gas & Parking", "Expenses"),
                FundMatcherTO.ByAccountLabelWithTransfer("ING old", "Work Income", "Work", "Expenses"),
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

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val responseBody = response.body<ProblemTO>()
        assertThat(responseBody.title).isEqualTo("Invalid import format")
    }

    @Test
    fun `test invalid import with missing account matcher`() = testApplication {
        configureEnvironmentWithDB { module() }

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val csvFile = File("src/test/resources/data/wallet_export.csv")
        val importConfiguration = ImportConfigurationTO(
            fileType = ImportFileTypeTO.WALLET_CSV,
            accountMatchers = listOf(
                AccountMatcherTO("Something else", "ING")
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByLabel("Basic - Food", "Expenses"),
                FundMatcherTO.ByLabel("Gifts", "Expenses"),
                FundMatcherTO.ByLabel("C&T - Gas & Parking", "Expenses"),
                FundMatcherTO.ByAccountLabelWithTransfer("ING old", "Work Income", "Work", "Expenses"),
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

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val responseBody = response.body<ProblemTO>()
        assertThat(responseBody.title).isEqualTo("Invalid import data")
    }
}
