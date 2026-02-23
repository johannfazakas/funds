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
import ro.jf.funds.importer.service.domain.ImportFile
import ro.jf.funds.importer.service.domain.ImportFileStatus
import ro.jf.funds.importer.service.persistence.ImportFileRepository
import ro.jf.funds.importer.service.domain.CreateImportFileResponse
import ro.jf.funds.importer.service.service.ImportFileService
import ro.jf.funds.platform.jvm.config.configureContentNegotiation
import ro.jf.funds.platform.jvm.config.configureDatabaseMigration
import ro.jf.funds.platform.jvm.config.configureDependencies
import ro.jf.funds.platform.jvm.test.extension.PostgresContainerExtension
import ro.jf.funds.platform.jvm.test.utils.configureEnvironment
import ro.jf.funds.platform.jvm.test.utils.createJsonHttpClient
import ro.jf.funds.platform.jvm.test.utils.dbConfig
import ro.jf.funds.platform.jvm.test.utils.kafkaConfig
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
class ImportFileApiTest {
    private val importFileService: ImportFileService = mock()
    private val importFileRepository: ImportFileRepository = mock()
    private val accountSdk: AccountSdk = mock()
    private val fundSdk: FundSdk = mock()
    private val labelSdk: LabelSdk = mock()
    private val conversionSdk: ConversionSdk = mock()
    private val transactionSdk: TransactionSdk = mock()

    @Test
    fun `given file name - when creating import file - then should return import file with upload url`() =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

            val httpClient = createJsonHttpClient()
            val userId = randomUUID()
            val importFileId = randomUUID()
            val importFile = ImportFile(
                importFileId = importFileId,
                userId = userId,
                fileName = "test.csv",
                type = ImportFileTypeTO.WALLET_CSV,
                s3Key = "$userId/test.csv",
                status = ImportFileStatus.PENDING,
            )
            whenever(importFileService.createImportFile(eq(userId), eq("test.csv"), eq(ImportFileTypeTO.WALLET_CSV)))
                .thenReturn(CreateImportFileResponse(importFile, "https://s3.example.com/upload-url"))

            val response = httpClient.post("/funds-api/import/v1/import-files") {
                header(USER_ID_HEADER, userId.toString())
                contentType(ContentType.Application.Json)
                setBody(CreateImportFileRequestTO(fileName = "test.csv", type = ImportFileTypeTO.WALLET_CSV))
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.Created)
            val responseBody = response.body<CreateImportFileResponseTO>()
            assertThat(responseBody.importFileId.toString()).isEqualTo(importFileId.toString())
            assertThat(responseBody.fileName).isEqualTo("test.csv")
            assertThat(responseBody.status).isEqualTo(ImportFileStatusTO.PENDING)
            assertThat(responseBody.uploadUrl).isEqualTo("https://s3.example.com/upload-url")
        }

    @Test
    fun `given uploaded file - when confirming upload - then should return uploaded import file`() =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

            val httpClient = createJsonHttpClient()
            val userId = randomUUID()
            val importFileId = randomUUID()
            val importFile = ImportFile(
                importFileId = importFileId,
                userId = userId,
                fileName = "test.csv",
                type = ImportFileTypeTO.WALLET_CSV,
                s3Key = "$userId/test.csv",
                status = ImportFileStatus.UPLOADED,
            )
            whenever(importFileService.confirmUpload(eq(userId), eq(importFileId)))
                .thenReturn(importFile)

            val response =
                httpClient.post("/funds-api/import/v1/import-files/$importFileId/confirm-upload") {
                    header(USER_ID_HEADER, userId.toString())
                }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val responseBody = response.body<ImportFileTO>()
            assertThat(responseBody.importFileId.toString()).isEqualTo(importFileId.toString())
            assertThat(responseBody.status).isEqualTo(ImportFileStatusTO.UPLOADED)
        }

    @Test
    fun `given no file - when confirming upload - then should return not found`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val importFileId = randomUUID()
        whenever(importFileService.confirmUpload(eq(userId), eq(importFileId)))
            .thenReturn(null)

        val response =
            httpClient.post("/funds-api/import/v1/import-files/$importFileId/confirm-upload") {
                header(USER_ID_HEADER, userId.toString())
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `given stored import files - when listing - then should return import file list`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        whenever(importFileService.listImportFiles(userId)).thenReturn(
            listOf(
                ImportFile(randomUUID(), userId, "file1.csv", ImportFileTypeTO.WALLET_CSV, "$userId/file1.csv", ImportFileStatus.PENDING),
                ImportFile(randomUUID(), userId, "file2.csv", ImportFileTypeTO.FUNDS_FORMAT_CSV, "$userId/file2.csv", ImportFileStatus.UPLOADED),
            )
        )

        val response = httpClient.get("/funds-api/import/v1/import-files") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val responseBody = response.body<List<ImportFileTO>>()
        assertThat(responseBody).hasSize(2)
        assertThat(responseBody[0].fileName).isEqualTo("file1.csv")
        assertThat(responseBody[0].status).isEqualTo(ImportFileStatusTO.PENDING)
        assertThat(responseBody[1].fileName).isEqualTo("file2.csv")
        assertThat(responseBody[1].status).isEqualTo(ImportFileStatusTO.UPLOADED)
    }

    @Test
    fun `given no import files - when listing - then should return empty list`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        whenever(importFileService.listImportFiles(userId)).thenReturn(emptyList())

        val response = httpClient.get("/funds-api/import/v1/import-files") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val responseBody = response.body<List<ImportFileTO>>()
        assertThat(responseBody).isEmpty()
    }

    @Test
    fun `given existing import file - when getting by id - then should return import file`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val importFileId = randomUUID()
        val importFile = ImportFile(importFileId, userId, "test.csv", ImportFileTypeTO.WALLET_CSV, "$userId/test.csv", ImportFileStatus.UPLOADED)
        whenever(importFileService.getImportFile(eq(userId), eq(importFileId)))
            .thenReturn(importFile)

        val response = httpClient.get("/funds-api/import/v1/import-files/$importFileId") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val responseBody = response.body<ImportFileTO>()
        assertThat(responseBody.importFileId.toString()).isEqualTo(importFileId.toString())
        assertThat(responseBody.fileName).isEqualTo("test.csv")
    }

    @Test
    fun `given no import file - when getting by id - then should return not found`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val importFileId = randomUUID()
        whenever(importFileService.getImportFile(eq(userId), eq(importFileId)))
            .thenReturn(null)

        val response = httpClient.get("/funds-api/import/v1/import-files/$importFileId") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `given uploaded import file - when requesting download url - then should return presigned url`() =
        testApplication {
            configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

            val httpClient = createJsonHttpClient()
            val userId = randomUUID()
            val importFileId = randomUUID()
            whenever(importFileService.generateDownloadUrl(eq(userId), eq(importFileId)))
                .thenReturn("https://s3.example.com/download-url")

            val response = httpClient.get("/funds-api/import/v1/import-files/$importFileId/download") {
                header(USER_ID_HEADER, userId.toString())
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val responseBody = response.body<DownloadUrlResponseTO>()
            assertThat(responseBody.downloadUrl).isEqualTo("https://s3.example.com/download-url")
        }

    @Test
    fun `given existing import file - when deleting - then should return no content`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val importFileId = randomUUID()
        whenever(importFileService.deleteImportFile(eq(userId), eq(importFileId)))
            .thenReturn(true)

        val response = httpClient.delete("/funds-api/import/v1/import-files/$importFileId") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
    }

    @Test
    fun `given no import file - when deleting - then should return not found`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val importFileId = randomUUID()
        whenever(importFileService.deleteImportFile(eq(userId), eq(importFileId)))
            .thenReturn(false)

        val response = httpClient.delete("/funds-api/import/v1/import-files/$importFileId") {
            header(USER_ID_HEADER, userId.toString())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `given no import file - when requesting download url - then should return not found`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, s3Config)

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val importFileId = randomUUID()
        whenever(importFileService.generateDownloadUrl(eq(userId), eq(importFileId)))
            .thenReturn(null)

        val response = httpClient.get("/funds-api/import/v1/import-files/$importFileId/download") {
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
