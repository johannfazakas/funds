package ro.jf.bk.user.service.config

import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import javax.sql.DataSource

fun Application.migrateDatabase(dataSource: DataSource) {
    Flyway.configure().dataSource(dataSource).load().migrate()
}
