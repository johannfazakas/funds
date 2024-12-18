package ro.jf.funds.reporting.service.config

import io.ktor.client.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import ro.jf.funds.commons.config.getEnvironmentProperty
import ro.jf.funds.commons.event.*
import ro.jf.funds.commons.persistence.getDataSource
import ro.jf.funds.commons.web.createHttpClient
import ro.jf.funds.reporting.api.event.REPORTING_DOMAIN
import ro.jf.funds.reporting.api.event.REPORT_VIEW_REQUEST
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.persistence.ReportViewTaskRepository
import ro.jf.funds.reporting.service.service.ReportViewService
import ro.jf.funds.reporting.service.service.ReportViewTaskService
import ro.jf.funds.reporting.service.service.event.CreateReportViewRequestHandler
import javax.sql.DataSource

val Application.reportingDependencies
    get() = module {
        includes(
            persistenceDependencies,
            eventProducerDependencies,
            integrationDependencies,
            serviceDependencies,
            eventConsumerDependencies
        )
    }

private val Application.persistenceDependencies
    get() = module {
        single<DataSource> { environment.getDataSource() }
        single<Database> { Database.connect(datasource = get()) }
        single<ReportViewTaskRepository> { ReportViewTaskRepository(get()) }
        single<ReportViewRepository> { ReportViewRepository(get()) }
    }

private val Application.eventProducerDependencies
    get() = module {
        single<TopicSupplier> { TopicSupplier(environment.getEnvironmentProperty()) }
        single<ProducerProperties> { ProducerProperties.fromEnv(environment) }
        single<Producer<CreateReportViewTO>> {
            createProducer(get(), get<TopicSupplier>().topic(REPORTING_DOMAIN, REPORT_VIEW_REQUEST))
        }
    }

private val Application.integrationDependencies
    get() = module {
        single<HttpClient> { createHttpClient() }
    }

private val Application.serviceDependencies
    get() = module {
        single<ReportViewService> { ReportViewService(get()) }
        single<ReportViewTaskService> { ReportViewTaskService(get(), get(), get()) }
        single<CreateReportViewRequestHandler> { CreateReportViewRequestHandler(get()) }
    }

private val Application.eventConsumerDependencies
    get() = module {
        single<ConsumerProperties> { ConsumerProperties.fromEnv(environment) }
        single<Consumer<CreateReportViewTO>> {
            createConsumer(
                get(),
                get<TopicSupplier>().topic(REPORTING_DOMAIN, REPORT_VIEW_REQUEST),
                get<CreateReportViewRequestHandler>()
            )
        }
    }
