package ro.jf.funds.importer.service.config

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import ro.jf.bk.account.sdk.AccountSdk
import ro.jf.bk.fund.sdk.FundSdk
import ro.jf.funds.importer.service.service.ImportHandler
import ro.jf.funds.importer.service.service.ImportService
import ro.jf.funds.importer.service.service.parser.CsvParser
import ro.jf.funds.importer.service.service.parser.ImportParserRegistry
import ro.jf.funds.importer.service.service.parser.WalletCsvImportParser

// TODO(Johann) should the rest be renamed to this pattern?
val Application.importServiceDependenciesModule
    get() = module {
        single<HttpClient> {
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
        single<CsvParser> { CsvParser() }
        single<WalletCsvImportParser> { WalletCsvImportParser(get()) }
        single<ImportParserRegistry> { ImportParserRegistry(get()) }
        single<AccountSdk> {
            AccountSdk(
                baseUrl = environment.config.property("integration.account-service.base-url").getString(), get()
            )
        }
        single<FundSdk> {
            FundSdk(
                baseUrl = environment.config.property("integration.fund-service.base-url").getString(), get()
            )
        }
        single<ImportHandler> { ImportHandler(get(), get()) }
        single<ImportService> { ImportService(get(), get()) }
    }
