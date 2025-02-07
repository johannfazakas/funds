package ro.jf.funds.fund.service.config

import io.ktor.client.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.StringQualifier
import org.koin.dsl.module
import ro.jf.funds.account.api.event.ACCOUNT_DOMAIN
import ro.jf.funds.account.api.event.ACCOUNT_TRANSACTIONS_REQUEST
import ro.jf.funds.account.api.event.ACCOUNT_TRANSACTIONS_RESPONSE
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.sdk.AccountSdk
import ro.jf.funds.account.sdk.AccountTransactionSdk
import ro.jf.funds.commons.config.getEnvironmentProperty
import ro.jf.funds.commons.event.*
import ro.jf.funds.commons.model.GenericResponse
import ro.jf.funds.commons.persistence.getDataSource
import ro.jf.funds.commons.persistence.getDbConnection
import ro.jf.funds.commons.web.createHttpClient
import ro.jf.funds.fund.api.event.FUND_DOMAIN
import ro.jf.funds.fund.api.event.FUND_TRANSACTIONS_REQUEST
import ro.jf.funds.fund.api.event.FUND_TRANSACTIONS_RESPONSE
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.service.persistence.FundRepository
import ro.jf.funds.fund.service.service.AccountTransactionAdapter
import ro.jf.funds.fund.service.service.FundService
import ro.jf.funds.fund.service.service.FundTransactionService
import ro.jf.funds.fund.service.service.event.CreateAccountTransactionsResponseHandler
import ro.jf.funds.fund.service.service.event.CreateFundTransactionsRequestHandler
import java.sql.Connection
import javax.sql.DataSource

val CREATE_ACCOUNT_TRANSACTIONS_RESPONSE_CONSUMER: Qualifier =
    StringQualifier("CreateAccountTransactionsResponse")
val CREATE_FUND_TRANSACTIONS_RESPONSE_PRODUCER: Qualifier =
    StringQualifier("CreateFundTransactionsResponse")

val Application.fundDependencies
    get() = module {
        includes(
            fundPersistenceDependencies,
            fundIntegrationDependencies,
            fundEventProducerDependencies,
            fundServiceDependencies,
            fundEventConsumerDependencies,
        )
    }

private val Application.fundPersistenceDependencies
    get() = module {
        single<DataSource> { environment.getDataSource() }
        single<Database> { Database.connect(datasource = get()) }
        single<Connection> { environment.getDbConnection() }
        single<FundRepository> { FundRepository(get()) }
    }

private val Application.fundIntegrationDependencies
    get() = module {
        single<HttpClient> { createHttpClient() }
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
    }

private val Application.fundEventProducerDependencies
    get() = module {
        single<TopicSupplier> { TopicSupplier(environment.getEnvironmentProperty()) }
        single<ProducerProperties> { ProducerProperties.fromEnv(environment) }
        single<Producer<CreateAccountTransactionsTO>> {
            createProducer(get(), get<TopicSupplier>().topic(ACCOUNT_DOMAIN, ACCOUNT_TRANSACTIONS_REQUEST))
        }
        single<Producer<GenericResponse>>(CREATE_FUND_TRANSACTIONS_RESPONSE_PRODUCER) {
            createProducer(get(), get<TopicSupplier>().topic(FUND_DOMAIN, FUND_TRANSACTIONS_RESPONSE))
        }
    }

private val Application.fundServiceDependencies
    get() = module {
        single<AccountTransactionAdapter> { AccountTransactionAdapter(get(), get()) }
        single<FundService> { FundService(get()) }
        single<FundTransactionService> { FundTransactionService(get(), get()) }
        single<CreateAccountTransactionsResponseHandler> {
            CreateAccountTransactionsResponseHandler(
                get<Producer<GenericResponse>>(
                    CREATE_FUND_TRANSACTIONS_RESPONSE_PRODUCER
                )
            )
        }
        single<CreateFundTransactionsRequestHandler> { CreateFundTransactionsRequestHandler(get()) }

    }

private val Application.fundEventConsumerDependencies
    get() = module {

        single<ConsumerProperties> { ConsumerProperties.fromEnv(environment) }
        single<Consumer<GenericResponse>>(CREATE_ACCOUNT_TRANSACTIONS_RESPONSE_CONSUMER) {
            createConsumer(
                get(),
                get<TopicSupplier>().topic(ACCOUNT_DOMAIN, ACCOUNT_TRANSACTIONS_RESPONSE),
                get<CreateAccountTransactionsResponseHandler>()
            )
        }
        single<Consumer<CreateFundTransactionsTO>> {
            createConsumer(
                get(),
                get<TopicSupplier>().topic(FUND_DOMAIN, FUND_TRANSACTIONS_REQUEST),
                get<CreateFundTransactionsRequestHandler>()
            )
        }
    }
