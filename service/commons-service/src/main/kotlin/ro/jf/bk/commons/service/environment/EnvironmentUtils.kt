package ro.jf.bk.commons.service.environment

import io.ktor.server.application.*

fun ApplicationEnvironment.getStringProperty(name: String): String = config.property(name).getString()
