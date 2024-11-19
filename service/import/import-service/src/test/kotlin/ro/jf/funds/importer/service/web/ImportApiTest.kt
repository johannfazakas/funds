package ro.jf.funds.importer.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.ktor.ext.get
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.error.ErrorTO
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.configureEnvironment
import ro.jf.funds.commons.test.utils.createJsonHttpClient
import ro.jf.funds.commons.test.utils.dbConfig
import ro.jf.funds.commons.test.utils.kafkaConfig
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.importer.api.model.*
import ro.jf.funds.importer.service.config.configureImportErrorHandling
import ro.jf.funds.importer.service.config.configureImportEventHandling
import ro.jf.funds.importer.service.config.configureImportRouting
import ro.jf.funds.importer.service.config.importDependencies
import java.io.File
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
class ImportApiTest {
    private val accountSdk: AccountSdk = mock()
    private val fundSdk: FundSdk = mock()
    private val historicalPricingSdk: HistoricalPricingSdk = mock()
    private val fundTransactionSdk: FundTransactionSdk = mock()

    @Test
    // TODO(Johann) clarify this, ByAccountLabelWithTransfer generates 3 records
    @Disabled("This test is not working, parser is emitting transaction with 3 records. why")
    fun `test valid import`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val csvFile = File("src/test/resources/data/wallet_export.csv")
        val importConfiguration = ImportConfigurationTO(
            fileType = ImportFileTypeTO.WALLET_CSV,
            accountMatchers = listOf(
                AccountMatcherTO("ING old", AccountName("ING"))
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByLabel("Basic - Food", FundName("Expenses")),
                FundMatcherTO.ByLabel("Gifts", FundName("Expenses")),
                FundMatcherTO.ByLabel("C&T - Gas & Parking", FundName("Expenses")),
                FundMatcherTO.ByAccountLabelWithTransfer(
                    "ING old",
                    "Work Income",
                    FundName("Work"),
                    FundName("Expenses")
                ),
            )
        )
        whenever(accountSdk.listAccounts(userId)).thenReturn(
            ListTO(
                listOf(
                    AccountTO(randomUUID(), AccountName("ING"), Currency.RON),
                )
            )
        )
        whenever(fundSdk.listFunds(userId)).thenReturn(
            ListTO(
                listOf(
                    FundTO(randomUUID(), FundName("Expenses")),
                    FundTO(randomUUID(), FundName("Work"))
                )
            )
        )
        whenever(fundTransactionSdk.createTransaction(eq(userId), any())).thenReturn(mock())

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

        assertThat(response.status).isEqualTo(HttpStatusCode.Accepted)
        val responseBody = response.body<ImportTaskTO>()
        assertThat(responseBody.taskId).isNotNull()
        assertThat(responseBody.status).isEqualTo(ImportTaskTO.Status.IN_PROGRESS)
    }

    @Test
    fun `test invalid import with missing configuration`(): Unit = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

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
        val responseBody = response.body<ErrorTO>()
        assertThat(responseBody.title).isEqualTo("Missing import configuration")
    }

    @Test
    fun `test invalid import with bad csv file`(): Unit = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val csvFile = File("src/test/resources/data/invalid_export.csv")
        val importConfiguration = ImportConfigurationTO(
            fileType = ImportFileTypeTO.WALLET_CSV,
            accountMatchers = listOf(
                AccountMatcherTO("ING old", AccountName("ING"))
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByLabel("Basic - Food", FundName("Expenses")),
                FundMatcherTO.ByLabel("Gifts", FundName("Expenses")),
                FundMatcherTO.ByLabel("C&T - Gas & Parking", FundName("Expenses")),
                FundMatcherTO.ByAccountLabelWithTransfer(
                    "ING old",
                    "Work Income",
                    FundName("Work"),
                    FundName("Expenses")
                ),
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
        val responseBody = response.body<ImportTaskTO>()
        assertThat(responseBody.status).isEqualTo(ImportTaskTO.Status.FAILED)
        assertThat(responseBody.reason).isNotEmpty()
    }

    @Test
    fun `test invalid import with missing account matcher`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val csvFile = File("src/test/resources/data/wallet_export.csv")
        val importConfiguration = ImportConfigurationTO(
            fileType = ImportFileTypeTO.WALLET_CSV,
            accountMatchers = listOf(
                AccountMatcherTO("Something else", AccountName("ING"))
            ),
            fundMatchers = listOf(
                FundMatcherTO.ByLabel("Basic - Food", FundName("Expenses")),
                FundMatcherTO.ByLabel("Gifts", FundName("Expenses")),
                FundMatcherTO.ByLabel("C&T - Gas & Parking", FundName("Expenses")),
                FundMatcherTO.ByAccountLabelWithTransfer(
                    "ING old",
                    "Work Income",
                    FundName("Work"),
                    FundName("Expenses")
                ),
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
        val responseBody = response.body<ImportTaskTO>()
        assertThat(responseBody.status).isEqualTo(ImportTaskTO.Status.FAILED)
        assertThat(responseBody.reason).isNotEmpty()
    }

    private fun Application.testModule() {
        val importAppTestModule = org.koin.dsl.module {
            single<AccountSdk> { accountSdk }
            single<FundSdk> { fundSdk }
            single<FundTransactionSdk> { fundTransactionSdk }
            single<HistoricalPricingSdk> { historicalPricingSdk }
        }
        configureDependencies(importDependencies, importAppTestModule)
        configureImportErrorHandling()
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureImportEventHandling()
        configureImportRouting()
    }
}
