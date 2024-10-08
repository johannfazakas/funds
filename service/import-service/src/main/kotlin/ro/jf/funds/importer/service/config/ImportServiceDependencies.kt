package ro.jf.funds.importer.service.config

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import ro.jf.funds.importer.service.domain.service.parser.CsvParser
import ro.jf.funds.importer.service.domain.service.parser.ImportParserRegistry
import ro.jf.funds.importer.service.domain.service.parser.WalletCsvImportParser
import ro.jf.funds.importer.service.domain.port.ImportService
import ro.jf.funds.importer.service.domain.service.ImportServiceImpl
import ro.jf.funds.importer.service.domain.service.ImportHandler

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
        single<ImportHandler> { ImportHandler() }
        single<ImportService> { ImportServiceImpl(get(), get()) }
    }
