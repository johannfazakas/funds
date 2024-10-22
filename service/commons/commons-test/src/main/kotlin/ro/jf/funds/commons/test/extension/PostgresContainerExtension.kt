package ro.jf.funds.commons.test.extension

import mu.KotlinLogging.logger
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

private val log = logger {}

object PostgresContainerExtension : BeforeAllCallback, AfterAllCallback {
    val jdbcUrl: String
        get() = runningContainer.jdbcUrl
    val username: String
        get() = runningContainer.username
    val password: String
        get() = runningContainer.password

    private val dockerImageName = DockerImageName.parse("postgres:15.2")

    // container will be reused on local if you have `testcontainers.reuse.enable=true` in ~/.testcontainers.properties
    private val container = PostgreSQLContainer(dockerImageName)
        .withDatabaseName("user_db")
        .withUsername("mock")
        .withPassword("mock")
        .withReuse(true)

    private val runningContainer: PostgreSQLContainer<*>
        get() = container.also { ensurePostgresRunning() }

    private fun ensurePostgresRunning() {
        container
            .takeIf { !it.isRunning }
            ?.apply {
                start()
                log.info("Started mock postgres @${jdbcUrl}")
            }
    }

    override fun beforeAll(context: ExtensionContext) {
        ensurePostgresRunning()
        migrateDB()
    }

    override fun afterAll(context: ExtensionContext?) {
        cleanDB()
    }

    private fun migrateDB() {
        log.info { "Migrate postgres @$jdbcUrl" }
        Flyway.configure().dataSource(jdbcUrl, username, password).load().migrate()
    }

    private fun cleanDB() {
        log.info { "Resetting postgres @$jdbcUrl" }
        Flyway.configure()
            .dataSource(jdbcUrl, username, password)
            .configuration(mapOf("flyway.cleanDisabled" to false.toString()))
            .load()
            .clean()
    }
}
