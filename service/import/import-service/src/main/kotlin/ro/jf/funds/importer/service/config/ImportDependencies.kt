package ro.jf.funds.importer.service.config

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.commons.config.getStringProperty
import ro.jf.funds.fund.sdk.FundSdk
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.importer.service.service.ImportHandler
import ro.jf.funds.importer.service.service.ImportService
import ro.jf.funds.importer.service.service.parser.CsvParser
import ro.jf.funds.importer.service.service.parser.ImportParserRegistry
import ro.jf.funds.importer.service.service.parser.WalletCsvImportParser

private const val ACCOUNT_SERVICE_BASE_URL_PROPERTY = "integration.account-service.base-url"
private const val FUND_SERVICE_BASE_URL_PROPERTY = "integration.fund-service.base-url"

val Application.importDependencies
    get() = module {
        // TODO(Johann) might be extracted as commons client
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
            AccountSdk(environment.getStringProperty(ACCOUNT_SERVICE_BASE_URL_PROPERTY), get())
        }
        single<FundSdk> {
            FundSdk(environment.getStringProperty(FUND_SERVICE_BASE_URL_PROPERTY), get())
        }
        single<FundTransactionSdk> {
            FundTransactionSdk(environment.getStringProperty(FUND_SERVICE_BASE_URL_PROPERTY), get())
        }
        single<ImportHandler> { ImportHandler(get(), get(), get()) }
        single<ImportService> { ImportService(get(), get()) }
    }
