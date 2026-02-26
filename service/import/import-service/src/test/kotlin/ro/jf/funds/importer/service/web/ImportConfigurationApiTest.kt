package ro.jf.funds.importer.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.ktor.ext.get
import org.mockito.kotlin.*
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.fund.sdk.AccountSdk
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.fund.sdk.LabelSdk
import ro.jf.funds.fund.sdk.TransactionSdk
import ro.jf.funds.importer.api.model.*
import ro.jf.funds.importer.service.config.configureImportErrorHandling
import ro.jf.funds.importer.service.config.configureImportRouting
import ro.jf.funds.importer.service.config.configureImportEventHandling
import ro.jf.funds.importer.service.config.importDependencyModules
import ro.jf.funds.importer.service.domain.ImportConfiguration
import ro.jf.funds.importer.service.domain.ImportMatchers
import ro.jf.funds.importer.service.domain.UpdateImportConfigurationCommand
import ro.jf.funds.importer.service.persistence.ImportConfigurationRepository
import ro.jf.funds.importer.service.persistence.ImportFileRepository
import ro.jf.funds.importer.service.service.ImportConfigurationService
import ro.jf.funds.importer.service.service.ImportFileService
import ro.jf.funds.platform.api.model.PageTO
import ro.jf.funds.platform.jvm.config.configureContentNegotiation
import ro.jf.funds.platform.jvm.config.configureDatabaseMigration
import ro.jf.funds.platform.jvm.config.configureDependencies
import ro.jf.funds.platform.jvm.persistence.PagedResult
import ro.jf.funds.platform.jvm.test.extension.PostgresContainerExtension
import ro.jf.funds.platform.jvm.test.utils.configureEnvironment
import ro.jf.funds.platform.jvm.test.utils.createJsonHttpClient
import ro.jf.funds.platform.jvm.test.utils.dbConfig
import ro.jf.funds.platform.jvm.test.utils.kafkaConfig
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import java.time.LocalDateTime
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
class ImportConfigurationApiTest {
    private val importConfigurationService: ImportConfigurationService = mock()
    private val importConfigurationRepository: ImportConfigurationRepository = mock()
    private val importFileService: ImportFileService = mock()
    private val importFileRepository: ImportFileRepository = mock()
    private val accountSdk: AccountSdk = mock()
    private val fundSdk: FundSdk = mock()
    private val labelSdk: LabelSdk = mock()
    private val conversionSdk: ConversionSdk = mock()
    private val transactionSdk: TransactionSdk = mock()

    @Test
    fun `given name and matchers - when creating import configuration - then should return created configuration`() =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

            val httpClient = createJsonHttpClient()
            val userId = randomUUID()
            val configId = randomUUID()
            val now = LocalDateTime.now()
            val matchers = ImportMatchers()

            whenever(
                importConfigurationService.createImportConfiguration(
                    eq(userId), eq("My Config"), any()
                )
            ).thenReturn(
                ImportConfiguration(configId, userId, "My Config", matchers, now)
            )

            val response = httpClient.post("/funds-api/import/v1/import-configurations") {
                header(USER_ID_HEADER, userId.toString())
                contentType(ContentType.Application.Json)
                setBody(CreateImportConfigurationRequest(name = "My Config"))
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.Created)
            val responseBody = response.body<ImportConfigurationTO>()
            assertThat(responseBody.importConfigurationId.toString()).isEqualTo(configId.toString())
            assertThat(responseBody.name).isEqualTo("My Config")
        }

    @Test
    fun `given existing configurations - when listing - then should return paginated configuration list`() =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

            val httpClient = createJsonHttpClient()
            val userId = randomUUID()
            val now = LocalDateTime.now()

            whenever(
                importConfigurationService.listImportConfigurations(eq(userId), anyOrNull(), anyOrNull())
            ).thenReturn(
                PagedResult(
                    listOf(
                        ImportConfiguration(randomUUID(), userId, "Config A", ImportMatchers(), now),
                        ImportConfiguration(randomUUID(), userId, "Config B", ImportMatchers(), now),
                    ),
                    2L
                )
            )

            val response = httpClient.get("/funds-api/import/v1/import-configurations") {
                header(USER_ID_HEADER, userId.toString())
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val responseBody = response.body<PageTO<ImportConfigurationTO>>()
            assertThat(responseBody.total).isEqualTo(2L)
            assertThat(responseBody.items).hasSize(2)
            assertThat(responseBody.items[0].name).isEqualTo("Config A")
            assertThat(responseBody.items[1].name).isEqualTo("Config B")
        }

    @Test
    fun `given no configurations - when listing - then should return empty page`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()

        whenever(
            importConfigurationService.listImportConfigurations(eq(userId), anyOrNull(), anyOrNull())
        ).thenReturn(PagedResult(emptyList(), 0L))

        val response = httpClient.get("/funds-api/import/v1/import-configurations") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val responseBody = response.body<PageTO<ImportConfigurationTO>>()
        assertThat(responseBody.total).isEqualTo(0L)
        assertThat(responseBody.items).isEmpty()
    }

    @Test
    fun `given existing configuration - when getting by id - then should return configuration`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val configId = randomUUID()
        val now = LocalDateTime.now()

        whenever(importConfigurationService.getImportConfiguration(eq(userId), eq(configId)))
            .thenReturn(ImportConfiguration(configId, userId, "My Config", ImportMatchers(), now))

        val response = httpClient.get("/funds-api/import/v1/import-configurations/$configId") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val responseBody = response.body<ImportConfigurationTO>()
        assertThat(responseBody.importConfigurationId.toString()).isEqualTo(configId.toString())
        assertThat(responseBody.name).isEqualTo("My Config")
    }

    @Test
    fun `given no configuration - when getting by id - then should return not found`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val configId = randomUUID()

        whenever(importConfigurationService.getImportConfiguration(eq(userId), eq(configId)))
            .thenReturn(null)

        val response = httpClient.get("/funds-api/import/v1/import-configurations/$configId") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `given existing configuration - when updating name - then should return updated configuration`() =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

            val httpClient = createJsonHttpClient()
            val userId = randomUUID()
            val configId = randomUUID()
            val now = LocalDateTime.now()

            whenever(
                importConfigurationService.updateImportConfiguration(
                    eq(userId), eq(configId), any()
                )
            ).thenReturn(
                ImportConfiguration(configId, userId, "Updated Config", ImportMatchers(), now)
            )

            val response = httpClient.put("/funds-api/import/v1/import-configurations/$configId") {
                header(USER_ID_HEADER, userId.toString())
                contentType(ContentType.Application.Json)
                setBody(UpdateImportConfigurationRequest(name = "Updated Config"))
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val responseBody = response.body<ImportConfigurationTO>()
            assertThat(responseBody.name).isEqualTo("Updated Config")
        }

    @Test
    fun `given no configuration - when updating - then should return not found`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val configId = randomUUID()

        whenever(
            importConfigurationService.updateImportConfiguration(eq(userId), eq(configId), any())
        ).thenReturn(null)

        val response = httpClient.put("/funds-api/import/v1/import-configurations/$configId") {
            header(USER_ID_HEADER, userId.toString())
            contentType(ContentType.Application.Json)
            setBody(UpdateImportConfigurationRequest(name = "Updated Config"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `given existing configuration - when deleting - then should return no content`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val configId = randomUUID()

        whenever(importConfigurationService.deleteImportConfiguration(eq(userId), eq(configId)))
            .thenReturn(true)

        val response = httpClient.delete("/funds-api/import/v1/import-configurations/$configId") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
    }

    @Test
    fun `given no configuration - when deleting - then should return not found`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val configId = randomUUID()

        whenever(importConfigurationService.deleteImportConfiguration(eq(userId), eq(configId)))
            .thenReturn(false)

        val response = httpClient.delete("/funds-api/import/v1/import-configurations/$configId") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    private fun Application.testModule() {
        val testOverrides = org.koin.dsl.module {
            single<AccountSdk> { accountSdk }
            single<FundSdk> { fundSdk }
            single<LabelSdk> { labelSdk }
            single<TransactionSdk> { transactionSdk }
            single<ConversionSdk> { conversionSdk }
            single<ImportFileRepository> { importFileRepository }
            single<ImportFileService> { importFileService }
            single<ImportConfigurationRepository> { importConfigurationRepository }
            single<ImportConfigurationService> { importConfigurationService }
        }
        configureDependencies(*importDependencyModules, testOverrides)
        configureImportErrorHandling()
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureImportEventHandling()
        configureImportRouting()
    }
}

private val s3Config
    get() = MapApplicationConfig(
        "s3.endpoint" to "http://localhost:4566",
        "s3.public-endpoint" to "http://localhost:4566",
        "s3.region" to "us-east-1",
        "s3.bucket" to "imports",
        "s3.access-key" to "test",
        "s3.secret-key" to "test",
        "s3.presigned-url-expiration" to "15m",
    )
