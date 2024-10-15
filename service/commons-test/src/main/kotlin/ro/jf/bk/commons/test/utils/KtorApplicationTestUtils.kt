package ro.jf.bk.commons.test.utils

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import ro.jf.bk.commons.test.extension.PostgresContainerExtension


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

private val dbConfig
    get() = MapApplicationConfig(
        "database.url" to PostgresContainerExtension.jdbcUrl,
        "database.user" to PostgresContainerExtension.username,
        "database.password" to PostgresContainerExtension.password,
    )
