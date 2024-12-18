package ro.jf.funds.importer.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.importer.service.config.configureImportErrorHandling
import ro.jf.funds.importer.service.config.configureImportEventHandling
import ro.jf.funds.importer.service.config.configureImportRouting
import ro.jf.funds.importer.service.config.importDependencies
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencies(importDependencies)
    configureImportErrorHandling()
    configureContentNegotiation()
    configureDatabaseMigration(get<DataSource>())
    configureImportEventHandling()
    configureImportRouting()
}
