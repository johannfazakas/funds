package ro.jf.funds.account.service.config

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import ro.jf.funds.account.service.persistence.AccountRepository
import ro.jf.funds.account.service.persistence.AccountTransactionRepository
import ro.jf.funds.account.service.service.AccountService
import ro.jf.funds.account.service.service.AccountTransactionService
import ro.jf.funds.commons.config.getEnvironmentProperty
import ro.jf.funds.commons.event.ConsumerProperties
import ro.jf.funds.commons.event.ProducerProperties
import ro.jf.funds.commons.event.TopicSupplier
import ro.jf.funds.commons.persistence.getDataSource
import ro.jf.funds.commons.persistence.getDbConnection
import javax.sql.DataSource


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
    }
