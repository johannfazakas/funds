package ro.jf.funds.commons.test.utils

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension


fun ApplicationTestBuilder.createJsonHttpClient() =
    createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

fun ApplicationTestBuilder.configureEnvironmentWithDB(
    configuration: ApplicationConfig = MapApplicationConfig(),
    module: Application.() -> Unit
) {
    environment {
        config = dbConfig.mergeWith(configuration)
    }
    application {
        module()
    }
}

// could use this one in all the tests
fun ApplicationTestBuilder.configureEnvironment(
    module: Application.() -> Unit,
    vararg configs: ApplicationConfig,
) {
    environment {
        config = configs.reduce(ApplicationConfig::mergeWith)
    }
    application {
        module()
    }
}

val dbConfig
    get() = MapApplicationConfig(
        "database.url" to PostgresContainerExtension.jdbcUrl,
        "database.user" to PostgresContainerExtension.username,
        "database.password" to PostgresContainerExtension.password,
    )

val kafkaConfig
    get() = MapApplicationConfig(
        "kafka.bootstrapServers" to KafkaContainerExtension.bootstrapServers,
        "kafka.groupId" to "test-group-id",
        "kafka.clientId" to "test-client-id",
    )
