package ro.jf.funds.commons.test.utils

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import ro.jf.funds.commons.event.TopicSupplier
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import java.util.UUID.randomUUID

private const val TEST_ENVIRONMENT = "test"

fun ApplicationTestBuilder.createJsonHttpClient() =
    createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

fun ApplicationTestBuilder.configureEnvironment(
    module: Application.() -> Unit,
    vararg configs: ApplicationConfig,
) {
    environment {
        config = configs.fold(environmentConfig, ApplicationConfig::mergeWith)
    }
    application {
        module()
    }
}

val environmentConfig
    get() = MapApplicationConfig(
        "environment" to TEST_ENVIRONMENT,
    )

val dbConfig
    get() = MapApplicationConfig(
        "database.url" to PostgresContainerExtension.jdbcUrl,
        "database.user" to PostgresContainerExtension.username,
        "database.password" to PostgresContainerExtension.password,
        "database.connection-pool-size" to 4.toString(),
    )

val kafkaConfig
    get() = MapApplicationConfig(
        "kafka.bootstrap-servers" to KafkaContainerExtension.bootstrapServers,
        "kafka.group-id" to "test-group-id-${randomUUID()}",
        "kafka.client-id" to "test-client-id",
    )

val testTopicSupplier by lazy { TopicSupplier(TEST_ENVIRONMENT) }
