package ro.jf.funds.analytics.service.config

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import ro.jf.funds.fund.api.event.FundEvents
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.analytics.service.service.TransactionsCreatedHandler
import ro.jf.funds.fund.api.model.TransactionsCreatedTO
import ro.jf.funds.platform.jvm.config.getEnvironmentProperty
import ro.jf.funds.platform.jvm.event.*
import ro.jf.funds.platform.jvm.persistence.getDataSource
import javax.sql.DataSource

val Application.analyticsDependencies
    get() = module {
        includes(
            analyticsPersistenceDependencies,
            analyticsServiceDependencies,
            analyticsEventConsumerDependencies,
        )
    }

private val Application.analyticsPersistenceDependencies
    get() = module {
        single<DataSource> { environment.getDataSource() }
        single<Database> { Database.connect(datasource = get()) }
        single<AnalyticsRecordRepository> { AnalyticsRecordRepository(get()) }
    }

private val Application.analyticsServiceDependencies
    get() = module {
        single<TransactionsCreatedHandler> { TransactionsCreatedHandler(get()) }
    }

private val Application.analyticsEventConsumerDependencies
    get() = module {
        single<TopicSupplier> { TopicSupplier(environment.getEnvironmentProperty()) }
        single<ConsumerProperties> { ConsumerProperties.fromEnv(environment) }
        single<Consumer<TransactionsCreatedTO>> {
            createConsumer(
                get(),
                get<TopicSupplier>().topic(FundEvents.FundTransactionsCreated),
                get<TransactionsCreatedHandler>()
            )
        }
    }
