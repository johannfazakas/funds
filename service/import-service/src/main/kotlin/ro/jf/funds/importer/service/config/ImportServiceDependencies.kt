package ro.jf.funds.importer.service.config

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import ro.jf.funds.importer.service.adapter.file.CsvParser

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
    }
