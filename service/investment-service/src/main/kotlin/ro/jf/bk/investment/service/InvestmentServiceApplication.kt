package ro.jf.bk.investment.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import ro.jf.bk.investment.service.config.configureDependencyInjection
import ro.jf.bk.investment.service.config.configureRouting
import ro.jf.bk.investment.service.config.configureSerialization

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencyInjection()
    configureSerialization()
    configureRouting()
}
