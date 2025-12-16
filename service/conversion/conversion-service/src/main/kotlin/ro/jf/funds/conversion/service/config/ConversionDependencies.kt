package ro.jf.funds.conversion.service.config

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import ro.jf.funds.platform.jvm.persistence.getDataSource
import ro.jf.funds.conversion.service.domain.InstrumentConversionSource
import ro.jf.funds.conversion.service.persistence.ConversionRepository
import ro.jf.funds.conversion.service.service.ConversionService
import ro.jf.funds.conversion.service.service.currency.converter.currencybeacon.CurrencyBeaconCurrencyConverter
import ro.jf.funds.conversion.service.service.instrument.InstrumentConverterRegistry
import ro.jf.funds.conversion.service.service.instrument.InstrumentConversionInfoRepository
import ro.jf.funds.conversion.service.service.instrument.converter.bt.BTInstrumentConverter
import ro.jf.funds.conversion.service.service.instrument.converter.financialtimes.FinancialTimesInstrumentConverter
import ro.jf.funds.conversion.service.service.instrument.converter.yahoo.YahooInstrumentConverter
import javax.sql.DataSource

val Application.conversionDependencies
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
        single { ConversionRepository(get()) }
        single { InstrumentConversionInfoRepository() }
        single { YahooInstrumentConverter(get()) }
        single { FinancialTimesInstrumentConverter(get()) }
        single { BTInstrumentConverter(get()) }
        single {
            InstrumentConverterRegistry(
                mapOf(
                    InstrumentConversionSource.YAHOO to get<YahooInstrumentConverter>(),
                    InstrumentConversionSource.FINANCIAL_TIMES to get<FinancialTimesInstrumentConverter>(),
                    InstrumentConversionSource.BT_ASSET_MANAGEMENT to get<BTInstrumentConverter>()
                )
            )
        }
        single { CurrencyBeaconCurrencyConverter(get()) }
        single { ConversionService(get(), get<CurrencyBeaconCurrencyConverter>(), get(), get()) }
    }
