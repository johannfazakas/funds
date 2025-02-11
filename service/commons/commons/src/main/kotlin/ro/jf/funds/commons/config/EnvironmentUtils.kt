package ro.jf.funds.commons.config

import io.ktor.server.application.*

private const val ENVIRONMENT_KEY = "environment"

fun ApplicationEnvironment.getStringProperty(name: String): String = config.property(name).getString()

fun ApplicationEnvironment.getEnvironmentProperty(): String = getStringProperty(ENVIRONMENT_KEY)
