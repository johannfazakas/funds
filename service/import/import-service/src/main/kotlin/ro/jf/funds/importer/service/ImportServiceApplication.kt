package ro.jf.funds.importer.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.funds.platform.jvm.config.configureContentNegotiation
import ro.jf.funds.platform.jvm.config.configureDatabaseMigration
import ro.jf.funds.platform.jvm.config.configureDependencies
import ro.jf.funds.platform.jvm.config.configureTracing
import ro.jf.funds.importer.service.config.configureImportErrorHandling
import ro.jf.funds.importer.service.config.configureImportEventHandling
import ro.jf.funds.importer.service.config.configureImportRouting
import ro.jf.funds.importer.service.config.importDependencyModules
import ro.jf.funds.platform.jvm.config.configureCors
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureTracing()
    configureDependencies(*importDependencyModules)
    configureImportErrorHandling()
    configureContentNegotiation()
    configureCors()
    configureDatabaseMigration(get<DataSource>())
    configureImportEventHandling()
    configureImportRouting()
}
