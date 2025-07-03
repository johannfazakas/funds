package ro.jf.funds.importer.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.config.configureTracing
import ro.jf.funds.importer.service.config.configureImportErrorHandling
import ro.jf.funds.importer.service.config.configureImportEventHandling
import ro.jf.funds.importer.service.config.configureImportRouting
import ro.jf.funds.importer.service.config.importDependencyModules
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencies(*importDependencyModules)
    configureImportErrorHandling()
    configureContentNegotiation()
    configureDatabaseMigration(get<DataSource>())
    configureImportEventHandling()
    configureImportRouting()
    configureTracing()
}
