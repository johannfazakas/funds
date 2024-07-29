package ro.jf.bk.investment.service.config

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
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
