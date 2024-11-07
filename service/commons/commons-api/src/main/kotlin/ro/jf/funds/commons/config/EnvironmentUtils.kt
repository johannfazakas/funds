package ro.jf.funds.commons.config

import io.ktor.server.application.*

fun ApplicationEnvironment.getStringProperty(name: String): String = config.property(name).getString()
