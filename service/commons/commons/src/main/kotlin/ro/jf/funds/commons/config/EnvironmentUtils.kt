package ro.jf.funds.commons.config

import io.ktor.server.application.*

private const val ENVIRONMENT_KEY = "environment"

fun ApplicationEnvironment.getStringProperty(name: String): String = config.property(name).getString()

fun ApplicationEnvironment.getIntProperty(name: String): Int =
    config.property(name).getString().toIntOrNull() ?: error("Property '$name' is not a valid integer")

fun ApplicationEnvironment.getBooleanPropertyOrNull(name: String): Boolean? =
    config.propertyOrNull(name)?.getString()?.toBoolean()

fun ApplicationEnvironment.getEnvironmentProperty(): String = getStringProperty(ENVIRONMENT_KEY)
