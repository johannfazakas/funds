package ro.jf.funds.reporting.service.config

import io.ktor.client.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import ro.jf.funds.commons.config.getEnvironmentProperty
import ro.jf.funds.commons.config.getStringProperty
import ro.jf.funds.commons.event.Producer
import ro.jf.funds.commons.event.ProducerProperties
import ro.jf.funds.commons.event.TopicSupplier
import ro.jf.funds.commons.event.createProducer
import ro.jf.funds.commons.persistence.getDataSource
import ro.jf.funds.commons.web.createHttpClient
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.reporting.api.event.REPORTING_DOMAIN
import ro.jf.funds.reporting.api.event.REPORT_VIEW_REQUEST
import ro.jf.funds.reporting.service.domain.CreateReportViewCommand
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.service.ReportViewService
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import ro.jf.funds.reporting.service.service.reportdata.ReportDataService
import ro.jf.funds.reporting.service.service.reportdata.ReportTransactionService
import ro.jf.funds.reporting.service.service.reportdata.resolver.*
import javax.sql.DataSource

private const val FUND_SERVICE_BASE_URL_PROPERTY = "integration.fund-service.base-url"
private const val HISTORICAL_PRICING_SERVICE_BASE_URL_PROPERTY = "integration.historical-pricing-service.base-url"

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
            createProducer(get(), get<TopicSupplier>().topic(REPORTING_DOMAIN, REPORT_VIEW_REQUEST))
        }
    }

private val Application.integrationDependencies
    get() = module {
        single<HttpClient> { createHttpClient() }
        single<FundTransactionSdk> {
            FundTransactionSdk(environment.getStringProperty(FUND_SERVICE_BASE_URL_PROPERTY), get())
        }
        single<HistoricalPricingSdk> {
            HistoricalPricingSdk(environment.getStringProperty(HISTORICAL_PRICING_SERVICE_BASE_URL_PROPERTY), get())
        }
    }

private val Application.serviceDependencies
    get() = module {
        single<ReportViewService> { ReportViewService(get()) }
        single<ConversionRateService> { ConversionRateService(get()) }
        single<GroupedNetDataResolver> { GroupedNetDataResolver(get()) }
        single<GroupedBudgetDataResolver> { GroupedBudgetDataResolver(get()) }
        single<ValueReportDataResolver> { ValueReportDataResolver(get()) }
        single<NetDataResolver> { NetDataResolver(get()) }
        single<PerformanceReportDataResolver> { PerformanceReportDataResolver(get()) }
        single<ReportDataResolverRegistry> { ReportDataResolverRegistry(get(), get(), get(), get(), get()) }
        single<ReportTransactionService> { ReportTransactionService(get()) }
        single<ReportDataService> { ReportDataService(get(), get(), get()) }
    }
