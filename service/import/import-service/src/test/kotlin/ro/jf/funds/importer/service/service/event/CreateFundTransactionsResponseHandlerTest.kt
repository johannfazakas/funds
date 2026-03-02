package ro.jf.funds.importer.service.service.event

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.importer.service.domain.CreateImportFileCommand
import ro.jf.funds.importer.service.domain.ImportFileStatus
import ro.jf.funds.importer.service.domain.ImportMatchers
import ro.jf.funds.importer.service.domain.CreateImportConfigurationCommand
import ro.jf.funds.importer.service.persistence.ImportConfigurationRepository
import ro.jf.funds.importer.service.persistence.ImportFileRepository
import ro.jf.funds.platform.jvm.error.ErrorTO
import ro.jf.funds.platform.jvm.event.*
import ro.jf.funds.platform.jvm.model.GenericResponse
import ro.jf.funds.platform.jvm.test.extension.KafkaContainerExtension
import ro.jf.funds.platform.jvm.test.extension.PostgresContainerExtension
import ro.jf.funds.platform.jvm.test.utils.configureEnvironment
import ro.jf.funds.platform.jvm.test.utils.dbConfig
import ro.jf.funds.platform.jvm.test.utils.kafkaConfig
import ro.jf.funds.platform.jvm.test.utils.testTopicSupplier
import ro.jf.funds.fund.api.event.FundEvents
import ro.jf.funds.importer.service.module
import java.time.Duration
import java.util.UUID.randomUUID

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class CreateFundTransactionsResponseHandlerTest {
    private val importConfigurationRepository = ImportConfigurationRepository(PostgresContainerExtension.connection)
    private val importFileRepository = ImportFileRepository(PostgresContainerExtension.connection)
    private val createTransactionsResponseTopic =
        testTopicSupplier.topic(FundEvents.FundTransactionsBatchResponse)
    private val fundTransactionsResponseProducer = createProducer<GenericResponse>(
        ProducerProperties(KafkaContainerExtension.bootstrapServers, "test-producer"),
        createTransactionsResponseTopic
    )

    private val userId = randomUUID()

    @AfterEach
    fun tearDown() = runBlocking {
        importFileRepository.deleteAll()
        importConfigurationRepository.deleteAll()
    }

    @Test
    fun `given success response should mark import file as imported`(): Unit = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig, integrationConfig)
        startApplication()

        val configuration = importConfigurationRepository.create(
            CreateImportConfigurationCommand(userId, "test-config", ImportMatchers())
        )
        val importFile = importFileRepository.create(
            CreateImportFileCommand(userId, "test.csv", ImportFileTypeTO.WALLET_CSV, configuration.importConfigurationId)
        )
        importFileRepository.updateStatus(userId, importFile.importFileId, ImportFileStatus.IMPORTING)

        val response = Event<GenericResponse>(userId, GenericResponse.Success, importFile.importFileId)
        fundTransactionsResponseProducer.send(response)

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val file = runBlocking { importFileRepository.findById(userId, importFile.importFileId) }
            assertThat(file).isNotNull
            assertThat(file?.status).isEqualTo(ImportFileStatus.IMPORTED)
        }
    }

    @Test
    fun `given error response should mark import file as failed`(): Unit = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig, integrationConfig)
        startApplication()

        val configuration = importConfigurationRepository.create(
            CreateImportConfigurationCommand(userId, "test-config", ImportMatchers())
        )
        val importFile = importFileRepository.create(
            CreateImportFileCommand(userId, "test.csv", ImportFileTypeTO.WALLET_CSV, configuration.importConfigurationId)
        )
        importFileRepository.updateStatus(userId, importFile.importFileId, ImportFileStatus.IMPORTING)

        val reason = ErrorTO("Title", "Detail")
        val response = Event<GenericResponse>(userId, GenericResponse.Error(reason), importFile.importFileId)
        fundTransactionsResponseProducer.send(response)

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val file = runBlocking { importFileRepository.findById(userId, importFile.importFileId) }
            assertThat(file).isNotNull
            assertThat(file?.status).isEqualTo(ImportFileStatus.IMPORT_FAILED)
            assertThat(file?.errors).hasSize(1)
            assertThat(file?.errors?.first()?.detail).isEqualTo("Detail")
        }
    }

    private val integrationConfig
        get() = MapApplicationConfig(
            "integration.account-service.base-url" to "localhost:8765",
            "integration.fund-service.base-url" to "localhost:8765",
            "integration.conversion-service.base-url" to "localhost:8765",
            "s3.endpoint" to "http://localhost:4566",
            "s3.public-endpoint" to "http://localhost:4566",
            "s3.region" to "us-east-1",
            "s3.bucket" to "imports",
            "s3.access-key" to "test",
            "s3.secret-key" to "test",
            "s3.presigned-url-expiration" to "15m",
        )
}
