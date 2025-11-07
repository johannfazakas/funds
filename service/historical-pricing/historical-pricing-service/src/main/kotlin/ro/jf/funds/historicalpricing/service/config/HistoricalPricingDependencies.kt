package ro.jf.funds.historicalpricing.service.config

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import ro.jf.funds.commons.persistence.getDataSource
import ro.jf.funds.historicalpricing.service.domain.HistoricalPriceSource
import ro.jf.funds.historicalpricing.service.persistence.HistoricalPriceRepository
import ro.jf.funds.historicalpricing.service.service.ConversionService
import ro.jf.funds.historicalpricing.service.service.currency.CurrencyService
import ro.jf.funds.historicalpricing.service.service.currency.converter.currencybeacon.CurrencyBeaconCurrencyConverter
import ro.jf.funds.historicalpricing.service.service.instrument.InstrumentConverterRegistry
import ro.jf.funds.historicalpricing.service.service.instrument.InstrumentService
import ro.jf.funds.historicalpricing.service.service.instrument.PricingInstrumentRepository
import ro.jf.funds.historicalpricing.service.service.instrument.converter.bt.BTInstrumentConverter
import ro.jf.funds.historicalpricing.service.service.instrument.converter.financialtimes.FinancialTimesInstrumentConverter
import ro.jf.funds.historicalpricing.service.service.instrument.converter.yahoo.YahooInstrumentConverter
import javax.sql.DataSource

val Application.historicalPricingDependencies
    get() = module {
        single<DataSource> { environment.getDataSource() }
        single<Database> { Database.connect(datasource = get()) }
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
        single { HistoricalPriceRepository(get()) }
        single { PricingInstrumentRepository() }
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
        single { InstrumentService(get(), get(), get(), get()) }
        single { ConversionService(get(), get()) }
    }
