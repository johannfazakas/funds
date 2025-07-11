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
import ro.jf.funds.commons.error.ErrorTO
import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.event.ProducerProperties
import ro.jf.funds.commons.event.createProducer
import ro.jf.funds.commons.model.GenericResponse
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.configureEnvironment
import ro.jf.funds.commons.test.utils.dbConfig
import ro.jf.funds.commons.test.utils.kafkaConfig
import ro.jf.funds.commons.test.utils.testTopicSupplier
import ro.jf.funds.fund.api.event.FUND_DOMAIN
import ro.jf.funds.fund.api.event.FUND_TRANSACTIONS_RESPONSE
import ro.jf.funds.importer.service.domain.ImportTaskPartStatus
import ro.jf.funds.importer.service.domain.StartImportTaskCommand
import ro.jf.funds.importer.service.module
import ro.jf.funds.importer.service.persistence.ImportTaskRepository
import java.time.Duration
import java.util.UUID.randomUUID

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class CreateFundTransactionsResponseHandlerTest {
    private val importTaskRepository = ImportTaskRepository(PostgresContainerExtension.connection)
    private val createTransactionsResponseTopic =
        testTopicSupplier.topic(FUND_DOMAIN, FUND_TRANSACTIONS_RESPONSE)
    private val fundTransactionsResponseProducer = createProducer<GenericResponse>(
        ProducerProperties(KafkaContainerExtension.bootstrapServers, "test-producer"),
        createTransactionsResponseTopic
    )

    private val userId = randomUUID()

    @AfterEach
    fun tearDown() = runBlocking {
        importTaskRepository.deleteAll()
    }


    @Test
    fun `should complete task on success`(): Unit = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig, integrationConfig)
        startApplication()

        val importTaskCommand = StartImportTaskCommand(userId, listOf("part1", "part2"))
        val importTask = importTaskRepository.startImportTask(importTaskCommand)
        val response = Event<GenericResponse>(userId, GenericResponse.Success, importTask.parts[0].taskPartId)
        fundTransactionsResponseProducer.send(response)

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val task = runBlocking { importTaskRepository.findImportTaskById(userId, importTask.taskId) }
            assertThat(task).isNotNull
            assertThat(task?.parts).isNotNull
            assertThat(task?.findPartByName("part1")).isNotNull
            assertThat(task?.findPartByName("part1")?.status).isEqualTo(ImportTaskPartStatus.COMPLETED)
        }
    }

    @Test
    fun `should fail task on error`(): Unit = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig, integrationConfig)
        startApplication()

        val importTaskCommand = StartImportTaskCommand(userId, listOf("part1", "part2"))
        val importTask = importTaskRepository.startImportTask(importTaskCommand)
        val reason = ErrorTO("Title", "Detail")
        val response =
            Event<GenericResponse>(userId, GenericResponse.Error(reason), importTask.parts[0].taskPartId)
        fundTransactionsResponseProducer.send(response)

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val task = runBlocking { importTaskRepository.findImportTaskById(userId, importTask.taskId) }
            assertThat(task).isNotNull
            assertThat(task?.findPartByName("part1")).isNotNull
            assertThat(task?.findPartByName("part1")?.status).isEqualTo(ImportTaskPartStatus.FAILED)
            assertThat(task?.findPartByName("part1")?.reason).isEqualTo(reason.detail)
        }
    }

    private val integrationConfig
        get() = MapApplicationConfig(
            "integration.account-service.base-url" to "localhost:8765",
            "integration.fund-service.base-url" to "localhost:8765",
            "integration.historical-pricing-service.base-url" to "localhost:8765",
        )
}
