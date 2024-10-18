package ro.jf.funds.importer.service.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import ro.jf.bk.account.api.model.AccountTO
import ro.jf.bk.account.sdk.AccountSdk
import ro.jf.bk.commons.model.ProblemTO
import ro.jf.bk.commons.service.config.configureContentNegotiation
import ro.jf.bk.commons.service.config.configureDependencies
import ro.jf.bk.commons.test.extension.MockServerExtension
import ro.jf.bk.commons.test.utils.configureEnvironmentWithDB
import ro.jf.bk.commons.test.utils.createJsonHttpClient
import ro.jf.bk.commons.web.USER_ID_HEADER
import ro.jf.bk.fund.api.model.FundTO
import ro.jf.bk.fund.sdk.FundSdk
import ro.jf.funds.importer.api.model.*
import ro.jf.funds.importer.service.config.configureRouting
import ro.jf.funds.importer.service.config.importServiceDependenciesModule
import java.io.File
import java.util.UUID.randomUUID

class ImportApiTest {
    private val accountSdk: AccountSdk = mock()
    private val fundSdk: FundSdk = mock()

    @Test
    fun `test valid import`() = testApplication {
        configureEnvironmentWithDB(appConfig) { testModule() }

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
        whenever(accountSdk.listAccounts(userId)).thenReturn(
            listOf(
                AccountTO.Currency(randomUUID(), "ING", "RON"),
            )
        )
        whenever(fundSdk.listFunds(userId)).thenReturn(
            listOf(
                FundTO(randomUUID(), "Expenses", listOf()),
                FundTO(randomUUID(), "Work", listOf())
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
        configureEnvironmentWithDB(appConfig) { testModule() }

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
        configureEnvironmentWithDB(appConfig) { testModule() }

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
        configureEnvironmentWithDB(appConfig) { testModule() }

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

    // TODO(Johann) not required, right?
    private val appConfig = MapApplicationConfig(
        "integration.account-service.base-url" to MockServerExtension.baseUrl,
        "integration.fund-service.base-url" to MockServerExtension.baseUrl
    )

    private fun Application.testModule() {
        val importAppTestModule = org.koin.dsl.module {
            single<AccountSdk> { accountSdk }
            single<FundSdk> { fundSdk }
        }
        configureDependencies(importServiceDependenciesModule, importAppTestModule)
        configureContentNegotiation()
        configureRouting()
    }
}
