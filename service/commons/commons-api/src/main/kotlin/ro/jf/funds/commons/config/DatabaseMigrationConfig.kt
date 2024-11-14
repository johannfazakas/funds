package ro.jf.funds.commons.config

import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import javax.sql.DataSource

// TODO(Johann) rename whole package to commons after commons-service is removed?
fun Application.configureDatabaseMigration(dataSource: DataSource) {
    Flyway.configure().dataSource(dataSource).load().migrate()
}
