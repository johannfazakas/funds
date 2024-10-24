package ro.jf.funds.account.service.config

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.postgresql.ds.PGSimpleDataSource
import ro.jf.funds.account.service.persistence.AccountRepository
import ro.jf.funds.account.service.persistence.AccountTransactionRepository
import ro.jf.funds.account.service.service.AccountService
import ro.jf.funds.account.service.service.AccountTransactionService
import java.sql.DriverManager
import javax.sql.DataSource

val Application.accountDependencies
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
            // TODO(Johann) could extract some common thing
            DriverManager.getConnection(
                environment.config.property("database.url").getString(),
                environment.config.property("database.user").getString(),
                environment.config.property("database.password").getString()
            )
        }
        single<AccountRepository> { AccountRepository(get()) }
        single<AccountService> { AccountService(get()) }
        single<AccountTransactionRepository> { AccountTransactionRepository(get()) }
        single<AccountTransactionService> { AccountTransactionService(get(), get()) }
    }