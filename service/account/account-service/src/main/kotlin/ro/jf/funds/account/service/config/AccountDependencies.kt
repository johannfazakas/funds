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
import ro.jf.funds.commons.model.GenericResponse
import ro.jf.funds.commons.persistence.getDataSource
import javax.sql.DataSource

val CREATE_ACCOUNT_TRANSACTIONS_RESPONSE_PRODUCER = StringQualifier("CreateAccountTransactionsResponse")

val Application.accountDependencyModules
    get() = arrayOf(
        accountDatabaseDependencies,
        accountKafkaDependencies,
        accountServiceDependencies,
    )

val Application.accountDatabaseDependencies
    get() = module {
        single<DataSource> { environment.getDataSource() }
        single<Database> { Database.connect(datasource = get()) }
        single<AccountRepository> { AccountRepository(get()) }
        single<AccountTransactionRepository> { AccountTransactionRepository(get()) }
    }

val Application.accountKafkaDependencies
    get() = module {
        single<TopicSupplier> { TopicSupplier(environment.getEnvironmentProperty()) }
        single<ConsumerProperties> { ConsumerProperties.fromEnv(environment) }
        single<ProducerProperties> { ProducerProperties.fromEnv(environment) }
        single<Producer<GenericResponse>>(CREATE_ACCOUNT_TRANSACTIONS_RESPONSE_PRODUCER) {
            createProducer(get(), get<TopicSupplier>().topic(ACCOUNT_DOMAIN, ACCOUNT_TRANSACTIONS_RESPONSE))
        }
        single<Consumer<CreateAccountTransactionsTO>> {
            createConsumer(
                get(),
                get<TopicSupplier>().topic(ACCOUNT_DOMAIN, ACCOUNT_TRANSACTIONS_REQUEST),
                get<CreateAccountTransactionsRequestHandler>()
            )
        }
    }

val Application.accountServiceDependencies
    get() = module {
        single<AccountService> { AccountService(get()) }
        single<AccountTransactionService> { AccountTransactionService(get(), get()) }
        single<CreateAccountTransactionsRequestHandler> {
            CreateAccountTransactionsRequestHandler(get(), get(CREATE_ACCOUNT_TRANSACTIONS_RESPONSE_PRODUCER))
        }
    }
