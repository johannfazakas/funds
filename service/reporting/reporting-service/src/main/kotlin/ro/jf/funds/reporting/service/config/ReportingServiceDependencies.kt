package ro.jf.funds.reporting.service.config

import io.ktor.client.*
import io.ktor.server.application.*
import org.koin.dsl.module
import ro.jf.funds.commons.web.createHttpClient
import ro.jf.funds.reporting.service.service.ReportViewService

val Application.reportingDependencies
    get() = module {
        single<HttpClient> { createHttpClient() }
        single<ReportViewService> { ReportViewService() }
    }
