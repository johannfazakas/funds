package ro.jf.funds.commons.test.extension

import mu.KotlinLogging.logger
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

private val log = logger {}

object PostgresContainerExtension : BeforeAllCallback, AfterAllCallback {
    private val dockerImageName = DockerImageName.parse("postgres:15.2")

    // container will be reused on local if you have `testcontainers.reuse.enable=true` in ~/.testcontainers.properties
    private val container = PostgreSQLContainer(dockerImageName)
        .withDatabaseName("mock")
        .withUsername("mock")
        .withPassword("mock")
        .withCommand("postgres -c max_connections=200")
        .withReuse(true)

    val jdbcUrl: String
        get() = runningContainer.jdbcUrl
    val username: String
        get() = runningContainer.username
    val password: String
        get() = runningContainer.password

    val connection: Database = Database.connect(
        url = jdbcUrl,
        user = username,
        password = password
    )

    private val runningContainer: PostgreSQLContainer<*>
        get() = container.apply { ensureRunning() }

    private fun PostgreSQLContainer<*>.ensureRunning() {
        if (!this.isRunning) {
            start()
            log.info { "Started postgres @${this.jdbcUrl}" }
        }
    }

    override fun beforeAll(context: ExtensionContext) {
        container.ensureRunning()
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
