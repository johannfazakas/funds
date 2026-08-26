package ro.jf.funds.analytics.service.config

import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import ro.jf.funds.conversion.sdk.ConversionSdk
import java.time.Duration
import ro.jf.funds.fund.api.event.FundEvents
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.analytics.service.domain.InterestRateCalculator
import ro.jf.funds.analytics.service.service.AnalyticsService
import ro.jf.funds.analytics.service.service.MetricResolutionService
import ro.jf.funds.analytics.service.service.InterestRateService
import ro.jf.funds.analytics.service.service.PerformanceService
import ro.jf.funds.analytics.service.domain.Series
import ro.jf.funds.analytics.service.service.series.*
import ro.jf.funds.analytics.service.service.TransactionsCreatedHandler
import ro.jf.funds.analytics.service.service.series.BalanceSeriesDefinition
import ro.jf.funds.analytics.service.service.series.CurrencyAmountsSeriesDefinition
import ro.jf.funds.analytics.service.service.series.CurrencyValueSeriesDefinition
import ro.jf.funds.analytics.service.service.series.CurrentInterestRateSeriesDefinition
import ro.jf.funds.analytics.service.service.series.CurrentInvestmentSeriesDefinition
import ro.jf.funds.analytics.service.service.series.CurrentProfitSeriesDefinition
import ro.jf.funds.analytics.service.service.series.InstrumentHoldingsSeriesDefinition
import ro.jf.funds.analytics.service.service.AnalyticsConversionService
import ro.jf.funds.analytics.service.service.series.SeriesDefinition
import ro.jf.funds.analytics.service.service.series.SeriesRegistry
import ro.jf.funds.analytics.service.service.series.NetChangeSeriesDefinition
import ro.jf.funds.analytics.service.service.series.OpenPositionRecordsSeriesDefinition
import ro.jf.funds.analytics.service.service.series.PairedPositionsSeriesDefinition
import ro.jf.funds.analytics.service.service.series.TotalInstrumentValueSeriesDefinition
import ro.jf.funds.analytics.service.service.series.TotalInterestRateSeriesDefinition
import ro.jf.funds.analytics.service.service.series.TotalInvestmentSeriesDefinition
import ro.jf.funds.analytics.service.service.series.TotalProfitSeriesDefinition
import ro.jf.funds.analytics.service.service.series.TransactionAmountsSeriesDefinition
import ro.jf.funds.fund.api.model.TransactionsCreatedTO
import ro.jf.funds.platform.jvm.config.getEnvironmentProperty
import ro.jf.funds.platform.jvm.config.getStringProperty
import ro.jf.funds.platform.jvm.event.*
import ro.jf.funds.platform.jvm.persistence.getDataSource
import ro.jf.funds.platform.jvm.web.createHttpClient
import javax.sql.DataSource

private const val CONVERSION_SERVICE_BASE_URL_PROPERTY = "integration.conversion-service.base-url"

val Application.analyticsDependencies
    get() = module {
        includes(
            analyticsPersistenceDependencies,
            analyticsIntegrationDependencies,
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

private val Application.analyticsIntegrationDependencies
    get() = module {
        single<HttpClient> { createHttpClient() }
        single<ConversionSdk> {
            ConversionSdk(
                baseUrl = environment.getStringProperty(CONVERSION_SERVICE_BASE_URL_PROPERTY),
                httpClient = get(),
                cache = Caffeine.newBuilder()
                    .maximumSize(100_000)
                    .expireAfterWrite(Duration.ofHours(24))
                    .build(),
            )
        }
    }

private val Application.analyticsServiceDependencies
    get() = module {
        single<TransactionsCreatedHandler> { TransactionsCreatedHandler(get()) }
        single<AnalyticsService> { AnalyticsService(get(), get()) }
        single<InterestRateCalculator> { InterestRateCalculator() }
        single<PerformanceService> { PerformanceService(get(), get()) }
        single<InterestRateService> { InterestRateService(get(), get(), get()) }
        single<SeriesRegistry> {
            val repository = get<AnalyticsRecordRepository>()
            val interestRateCalculator = get<InterestRateCalculator>()
            val conversions = AnalyticsConversionService(get<ConversionSdk>())
            val definitions: List<SeriesDefinition<*>> = listOf(
                TransactionAmountsSeriesDefinition(repository),
                OpenPositionRecordsSeriesDefinition(repository),
                InstrumentHoldingsSeriesDefinition(repository),
                CurrencyAmountsSeriesDefinition(repository),
                PairedPositionsSeriesDefinition(),
                BalanceSeriesDefinition(conversions),
                NetChangeSeriesDefinition(conversions),
                TotalInvestmentSeriesDefinition(conversions),
                CurrentInvestmentSeriesDefinition(conversions),
                TotalInstrumentValueSeriesDefinition(conversions),
                CurrencyValueSeriesDefinition(conversions),
                TotalProfitSeriesDefinition(),
                CurrentProfitSeriesDefinition(),
                TotalInterestRateSeriesDefinition(conversions, interestRateCalculator),
                CurrentInterestRateSeriesDefinition(conversions, interestRateCalculator),
            )
            val missing = Series.entries.filterNot { series -> definitions.any { it.series == series } }
            require(missing.isEmpty()) { "Series without registered definitions: $missing" }
            SeriesRegistry(definitions)
        }
        single<MetricResolutionService> { MetricResolutionService(get()) }
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
