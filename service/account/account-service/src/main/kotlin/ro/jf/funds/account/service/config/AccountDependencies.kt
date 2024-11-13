package ro.jf.funds.account.service.config

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.core.qualifier.StringQualifier
import org.koin.dsl.module
import ro.jf.funds.account.api.event.ACCOUNT_DOMAIN
import ro.jf.funds.account.api.event.ACCOUNT_TRANSACTIONS_REQUEST
import ro.jf.funds.account.api.event.ACCOUNT_TRANSACTIONS_RESPONSE
import ro.jf.funds.account.api.model.CreateAccountTransactionsTO
import ro.jf.funds.account.service.persistence.AccountRepository
import ro.jf.funds.account.service.persistence.AccountTransactionRepository
import ro.jf.funds.account.service.service.AccountService
import ro.jf.funds.account.service.service.AccountTransactionService
import ro.jf.funds.account.service.service.event.CreateAccountTransactionsRequestHandler
import ro.jf.funds.commons.config.getEnvironmentProperty
import ro.jf.funds.commons.event.*
import ro.jf.funds.commons.event.RequestConsumer.Companion.createRequestConsumer
import ro.jf.funds.commons.model.GenericResponse
import ro.jf.funds.commons.persistence.getDataSource
import ro.jf.funds.commons.persistence.getDbConnection
import javax.sql.DataSource

val CREATE_ACCOUNT_TRANSACTIONS_RESPONSE_PRODUCER = StringQualifier("CreateAccountTransactionsResponse")

val Application.accountDependencies
    get() = module {
        single<DataSource> { environment.getDataSource() }
        single<Database> { Database.connect(datasource = get()) }
        single { environment.getDbConnection() }
        single<TopicSupplier> { TopicSupplier(environment.getEnvironmentProperty()) }
        single<ConsumerProperties> { ConsumerProperties.fromEnv(environment) }
        single<ProducerProperties> { ProducerProperties.fromEnv(environment) }
        single<AccountRepository> { AccountRepository(get()) }
        single<AccountService> { AccountService(get()) }
        single<AccountTransactionRepository> { AccountTransactionRepository(get()) }
        single<AccountTransactionService> { AccountTransactionService(get(), get()) }
        single<TopicSupplier> { TopicSupplier(environment.getEnvironmentProperty()) }
        single<ResponseProducer<GenericResponse>>(CREATE_ACCOUNT_TRANSACTIONS_RESPONSE_PRODUCER) {
            createResponseProducer(get(), get<TopicSupplier>().topic(ACCOUNT_DOMAIN, ACCOUNT_TRANSACTIONS_RESPONSE))
        }
        single<CreateAccountTransactionsRequestHandler> {
            CreateAccountTransactionsRequestHandler(get(), get(CREATE_ACCOUNT_TRANSACTIONS_RESPONSE_PRODUCER))
        }
        single<RequestConsumer<CreateAccountTransactionsTO>> {
            createRequestConsumer(
                get(),
                get<TopicSupplier>().topic(ACCOUNT_DOMAIN, ACCOUNT_TRANSACTIONS_REQUEST),
                get<CreateAccountTransactionsRequestHandler>()
            )
        }
    }
