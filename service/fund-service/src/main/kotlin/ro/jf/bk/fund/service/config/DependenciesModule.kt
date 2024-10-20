package ro.jf.bk.fund.service.config

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import org.postgresql.ds.PGSimpleDataSource
import ro.jf.bk.account.sdk.AccountSdk
import ro.jf.bk.account.sdk.AccountTransactionSdk
import ro.jf.bk.fund.service.persistence.FundRepository
import ro.jf.bk.fund.service.service.AccountSdkAdapter
import ro.jf.bk.fund.service.service.AccountTransactionAdapter
import ro.jf.bk.fund.service.service.FundService
import ro.jf.bk.fund.service.service.FundTransactionService
import java.sql.DriverManager
import javax.sql.DataSource

val Application.fundsAppModule
    get() = module {
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
        single<FundRepository> { FundRepository(get()) }
        single<AccountSdk> {
            AccountSdk(
                environment.config.property("integration.account-service.base-url").getString(), get()
            )
        }
        single<AccountTransactionSdk> {
            AccountTransactionSdk(
                environment.config.property("integration.account-service.base-url").getString(), get()
            )
        }
        single<AccountSdkAdapter> { AccountSdkAdapter(get()) }
        single<AccountTransactionAdapter> { AccountTransactionAdapter(get()) }
        single<FundService> { FundService(get(), get()) }
        single<FundTransactionService> { FundTransactionService(get()) }
    }
