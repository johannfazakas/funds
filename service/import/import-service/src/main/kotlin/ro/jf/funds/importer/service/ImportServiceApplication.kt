package ro.jf.funds.importer.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import ro.jf.funds.commons.service.config.configureContentNegotiation
import ro.jf.funds.commons.service.config.configureDependencies
import ro.jf.funds.importer.service.config.configureImportErrorHandling
import ro.jf.funds.importer.service.config.configureImportRouting
import ro.jf.funds.importer.service.config.importDependencies

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencies(importDependencies)
    configureImportErrorHandling()
    configureContentNegotiation()
    configureImportRouting()
}
