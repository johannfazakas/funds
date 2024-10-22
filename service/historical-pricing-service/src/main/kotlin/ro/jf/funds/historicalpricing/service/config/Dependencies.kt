package ro.jf.funds.historicalpricing.service.config

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import ro.jf.funds.historicalpricing.api.model.HistoricalPriceSource
import ro.jf.funds.historicalpricing.service.domain.service.currency.CurrencyPairHistoricalPriceRepository
import ro.jf.funds.historicalpricing.service.domain.service.currency.CurrencyService
import ro.jf.funds.historicalpricing.service.domain.service.instrument.InstrumentConverterRegistry
import ro.jf.funds.historicalpricing.service.domain.service.instrument.InstrumentHistoricalPriceRepository
import ro.jf.funds.historicalpricing.service.infra.converter.currency.currencybeacon.CurrencyBeaconCurrencyConverter
import ro.jf.funds.historicalpricing.service.infra.converter.instrument.bt.BTInstrumentConverter
import ro.jf.funds.historicalpricing.service.infra.converter.instrument.financialtimes.FinancialTimesInstrumentConverter
import ro.jf.funds.historicalpricing.service.infra.converter.instrument.yahoo.YahooInstrumentConverter
import ro.jf.funds.historicalpricing.service.infra.persistence.CurrencyPairHistoricalPriceExposedRepository
import ro.jf.funds.historicalpricing.service.infra.persistence.InstrumentHistoricalPriceExposedRepository
import java.sql.DriverManager

fun Application.configureDependencyInjection() {
    install(Koin) {
        modules(modules = module {
            single<Database> {
                Database.connect(
                    url = environment.config.property("database.url").getString(),
                    user = environment.config.property("database.user").getString(),
                    password = environment.config.property("database.password").getString(),
                )
            }
            single {
                DriverManager.getConnection(
                    environment.config.property("database.url").getString(),
                    environment.config.property("database.user").getString(),
                    environment.config.property("database.password").getString()
                )
            }
            single {
                HttpClient(CIO) {
                    install(ContentNegotiation) {
                        json(Json {
                            prettyPrint = true
                            isLenient = true
                            ignoreUnknownKeys = true
                        })
                    }
                }
            }
            single<CurrencyPairHistoricalPriceRepository> { CurrencyPairHistoricalPriceExposedRepository(get()) }
            single<InstrumentHistoricalPriceRepository> { InstrumentHistoricalPriceExposedRepository(get()) }
            single { YahooInstrumentConverter(get()) }
            single { FinancialTimesInstrumentConverter(get()) }
            single { BTInstrumentConverter(get()) }
            single {
                InstrumentConverterRegistry(
                    mapOf(
                        HistoricalPriceSource.YAHOO to get<YahooInstrumentConverter>(),
                        HistoricalPriceSource.FINANCIAL_TIMES to get<FinancialTimesInstrumentConverter>(),
                        HistoricalPriceSource.BT_ASSET_MANAGEMENT to get<BTInstrumentConverter>()
                    )
                )
            }
            single { CurrencyBeaconCurrencyConverter(get()) }
            single { CurrencyService(get<CurrencyBeaconCurrencyConverter>(), get()) }
            single {
                ro.jf.funds.historicalpricing.service.domain.service.instrument.InstrumentService(
                    get(),
                    get(),
                    get()
                )
            }
        })
    }
}
