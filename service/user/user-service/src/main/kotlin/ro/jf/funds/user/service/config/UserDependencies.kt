package ro.jf.funds.user.service.config

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import ro.jf.funds.commons.persistence.getDataSource
import ro.jf.funds.commons.persistence.getDbConnection
import ro.jf.funds.user.service.adapter.persistence.UserExposedRepository
import ro.jf.funds.user.service.domain.port.UserRepository
import javax.sql.DataSource

val Application.userDependencies
    get() = module {
        single<DataSource> { environment.getDataSource() }
        single<Database> { Database.connect(datasource = get()) }
        single { environment.getDbConnection() }
        single<UserRepository> { UserExposedRepository(get()) }
//            TODO(Johann)!! is the HttpClient dependency needed? Historical Pricing could be used as an sdk.
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
    }
