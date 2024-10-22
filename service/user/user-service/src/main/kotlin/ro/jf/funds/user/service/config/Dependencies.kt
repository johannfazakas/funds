package ro.jf.funds.user.service.config

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.postgresql.ds.PGSimpleDataSource
import ro.jf.funds.user.service.adapter.persistence.UserExposedRepository
import ro.jf.funds.user.service.domain.port.UserRepository
import java.sql.DriverManager
import javax.sql.DataSource


fun Application.configureDependencies() {
    install(Koin) {
        modules(modules = module {
            // TODO(Johann) common module could be extracted
            single<DataSource> {
                PGSimpleDataSource().apply {
                    setURL(environment.config.property("database.url").getString())
                    user = environment.config.property("database.user").getString()
                    password = environment.config.property("database.password").getString()
                }
            }
            single<Database> { Database.connect(datasource = get()) }
            single {
                DriverManager.getConnection(
                    environment.config.property("database.url").getString(),
                    environment.config.property("database.user").getString(),
                    environment.config.property("database.password").getString()
                )
            }
            single<UserRepository> { UserExposedRepository(get()) }
//            Johann!! is the HttpClient dependency needed? Historical Pricing could be used as an sdk.
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
        })
    }
}
