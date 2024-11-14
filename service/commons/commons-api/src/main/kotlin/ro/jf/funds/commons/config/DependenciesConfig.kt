package ro.jf.funds.commons.config

import io.ktor.server.application.*
import org.koin.core.module.Module
import org.koin.ktor.plugin.Koin

fun Application.configureDependencies(vararg modules: Module) {
    install(Koin) {
        modules(*modules)
    }
}
