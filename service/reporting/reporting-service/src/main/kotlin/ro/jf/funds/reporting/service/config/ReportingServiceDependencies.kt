package ro.jf.funds.reporting.service.config

import io.ktor.client.*
import io.ktor.server.application.*
import org.koin.dsl.module
import ro.jf.funds.commons.web.createHttpClient

val Application.reportingDependencies
    get() = module {
        single<HttpClient> { createHttpClient() }
    }