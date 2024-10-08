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
import ro.jf.bk.account.sdk.TransactionSdk
import ro.jf.bk.fund.service.adapter.client.AccountSdkAdapter
import ro.jf.bk.fund.service.adapter.client.TransactionSdkAdapter
import ro.jf.bk.fund.service.adapter.persistence.FundExposedRepository
import ro.jf.bk.fund.service.domain.port.*
import ro.jf.bk.fund.service.domain.service.FundServiceImpl
import ro.jf.bk.fund.service.domain.service.TransactionServiceImpl
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
        single<FundRepository> { FundExposedRepository(get()) }
        single<AccountSdk> {
            AccountSdk(
                environment.config.property("integration.account-service.base-url").getString(), get()
            )
        }
        single<TransactionSdk> {
            TransactionSdk(
                environment.config.property("integration.account-service.base-url").getString(), get()
            )
        }
        single<AccountRepository> { AccountSdkAdapter(get()) }
        single<TransactionRepository> { TransactionSdkAdapter(get()) }
        single<FundService> { FundServiceImpl(get(), get()) }
        single<TransactionService> { TransactionServiceImpl(get()) }
    }
