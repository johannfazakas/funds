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
import ro.jf.funds.importer.api.model.ImportTaskTO
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

        val importTask = importTaskRepository.save(userId, ImportTaskTO.Status.IN_PROGRESS)
        val response = Event<GenericResponse>(userId, GenericResponse.Success, importTask.taskId)
        fundTransactionsResponseProducer.send(response)

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val task = runBlocking { importTaskRepository.findById(userId, importTask.taskId) }
            assertThat(task).isNotNull
            assertThat(task!!.status).isEqualTo(ImportTaskTO.Status.COMPLETED)
        }
    }

    @Test
    fun `should fail task on error`(): Unit = testApplication {
        configureEnvironment(Application::module, dbConfig, kafkaConfig, integrationConfig)
        startApplication()

        val importTask = importTaskRepository.save(userId, ImportTaskTO.Status.IN_PROGRESS)
        val response =
            Event<GenericResponse>(userId, GenericResponse.Error(ErrorTO("Title", "Detail")), importTask.taskId)
        fundTransactionsResponseProducer.send(response)

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val task = runBlocking { importTaskRepository.findById(userId, importTask.taskId) }
            assertThat(task).isNotNull
            assertThat(task!!.status).isEqualTo(ImportTaskTO.Status.FAILED)
        }
    }

    private val integrationConfig
        get() = MapApplicationConfig(
            "integration.account-service.base-url" to "localhost:8765",
            "integration.fund-service.base-url" to "localhost:8765",
        )
}
