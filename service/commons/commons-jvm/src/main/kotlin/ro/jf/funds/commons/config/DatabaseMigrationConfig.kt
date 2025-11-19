package ro.jf.funds.commons.config

import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import javax.sql.DataSource

fun Application.configureDatabaseMigration(dataSource: DataSource) {
    Flyway.configure().dataSource(dataSource).load().migrate()
}
