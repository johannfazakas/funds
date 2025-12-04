package ro.jf.funds.reporting.service.config

import io.ktor.client.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import ro.jf.funds.commons.config.getEnvironmentProperty
import ro.jf.funds.commons.config.getStringProperty
import ro.jf.funds.commons.event.*
import ro.jf.funds.commons.persistence.getDataSource
import ro.jf.funds.commons.web.createHttpClient
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.fund.sdk.TransactionSdk
import ro.jf.funds.reporting.api.event.ReportingEvents
import ro.jf.funds.reporting.service.domain.CreateReportViewCommand
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.service.ReportViewService
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import ro.jf.funds.reporting.service.service.reportdata.InterestRateCalculator
import ro.jf.funds.reporting.service.service.reportdata.ReportDataService
import ro.jf.funds.reporting.service.service.reportdata.ReportTransactionService
import ro.jf.funds.reporting.service.service.reportdata.forecast.ForecastStrategy
import ro.jf.funds.reporting.service.service.reportdata.forecast.LinearRegressionForecastStrategy
import ro.jf.funds.reporting.service.service.reportdata.resolver.*
import javax.sql.DataSource

private const val FUND_SERVICE_BASE_URL_PROPERTY = "integration.fund-service.base-url"
private const val CONVERSION_SERVICE_BASE_URL_PROPERTY = "integration.conversion-service.base-url"

val Application.reportingDependencies
    get() = module {
        includes(
            persistenceDependencies,
            eventProducerDependencies,
            integrationDependencies,
            serviceDependencies,
        )
    }

private val Application.persistenceDependencies
    get() = module {
        single<DataSource> { environment.getDataSource() }
        single<Database> { Database.connect(datasource = get()) }
        single<ReportViewRepository> { ReportViewRepository(get()) }
    }

private val Application.eventProducerDependencies
    get() = module {
        single<TopicSupplier> { TopicSupplier(environment.getEnvironmentProperty()) }
        single<ProducerProperties> { ProducerProperties.fromEnv(environment) }
        single<Producer<CreateReportViewCommand>> {
            createProducer(get(), get<TopicSupplier>().topic(ReportingEvents.FundTransactionsBatchRequest))
        }
    }

private val Application.integrationDependencies
    get() = module {
        single<HttpClient> { createHttpClient() }
        single<TransactionSdk> {
            TransactionSdk(environment.getStringProperty(FUND_SERVICE_BASE_URL_PROPERTY), get())
        }
        single<ConversionSdk> {
            ConversionSdk(environment.getStringProperty(CONVERSION_SERVICE_BASE_URL_PROPERTY), get())
        }
    }

private val Application.serviceDependencies
    get() = module {
        single<ReportViewService> { ReportViewService(get()) }
        single<ConversionRateService> { ConversionRateService(get()) }
        single<InterestRateCalculator> { InterestRateCalculator() }
        single<ForecastStrategy> { LinearRegressionForecastStrategy() }
        single<GroupedNetDataResolver> { GroupedNetDataResolver(get(), get()) }
        single<GroupedBudgetDataResolver> { GroupedBudgetDataResolver(get(), get()) }
        single<ValueReportDataResolver> { ValueReportDataResolver(get(), get()) }
        single<NetDataResolver> { NetDataResolver(get(), get()) }
        single<PerformanceReportDataResolver> { PerformanceReportDataResolver(get(), get()) }
        single<InstrumentPerformanceReportDataResolver> { InstrumentPerformanceReportDataResolver(get(), get()) }
        single<InterestRateReportResolver> { InterestRateReportResolver(get(), get(), get()) }
        single<InstrumentInterestRateReportResolver> { InstrumentInterestRateReportResolver(get(), get(), get()) }
        single<ReportDataResolverRegistry> {
            ReportDataResolverRegistry(
                get(), get(), get(), get(), get(), get(), get(), get()
            )
        }
        single<ReportTransactionService> { ReportTransactionService(get()) }
        single<ReportDataService> { ReportDataService(get(), get(), get()) }
    }
