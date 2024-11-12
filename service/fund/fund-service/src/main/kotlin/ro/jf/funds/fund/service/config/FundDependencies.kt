package ro.jf.funds.fund.service.config

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import ro.jf.funds.account.api.event.ACCOUNT_DOMAIN
import ro.jf.funds.account.api.event.ACCOUNT_TRANSACTIONS_REQUEST
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.account.sdk.AccountTransactionSdk
import ro.jf.funds.commons.config.getEnvironmentProperty
import ro.jf.funds.commons.event.*
import ro.jf.funds.commons.persistence.getDataSource
import ro.jf.funds.commons.persistence.getDbConnection
import ro.jf.funds.fund.service.persistence.FundRepository
import ro.jf.funds.fund.service.service.AccountTransactionAdapter
import ro.jf.funds.fund.service.service.FundService
import ro.jf.funds.fund.service.service.FundTransactionService
import java.sql.Connection
import javax.sql.DataSource

val Application.fundDependencies
    get() = module {
        single<DataSource> { environment.getDataSource() }
        single<Database> { Database.connect(datasource = get()) }
        single<Connection> { environment.getDbConnection() }
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
        // TODO(Johann) common kafka stuff could be extracted as koin module
        single<TopicSupplier> { TopicSupplier(environment.getEnvironmentProperty()) }
        single<ConsumerProperties> { ConsumerProperties.fromEnv(environment) }
        single<ProducerProperties> { ProducerProperties.fromEnv(environment) }
        single<RequestProducer<CreateAccountTransactionsTO>> {
            createRequestProducer(get(), get<TopicSupplier>().topic(ACCOUNT_DOMAIN, ACCOUNT_TRANSACTIONS_REQUEST))
        }
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
        single<AccountTransactionAdapter> { AccountTransactionAdapter(get(), get()) }
        single<FundService> { FundService(get()) }
        single<FundTransactionService> { FundTransactionService(get()) }
    }
