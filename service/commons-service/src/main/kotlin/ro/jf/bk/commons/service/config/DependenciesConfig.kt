package ro.jf.bk.commons.service.config

import io.ktor.server.application.*
import org.koin.core.module.Module
import org.koin.ktor.plugin.Koin

fun Application.configureDependencies(vararg modules: Module) {
    install(Koin) {
        modules(*modules)
    }
}
