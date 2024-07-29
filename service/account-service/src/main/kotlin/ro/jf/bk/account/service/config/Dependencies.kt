package ro.jf.bk.account.service.config

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.postgresql.ds.PGSimpleDataSource
import ro.jf.bk.account.service.adapter.persistence.AccountExposedRepository
import ro.jf.bk.account.service.adapter.persistence.TransactionExposedRepository
import ro.jf.bk.account.service.domain.port.AccountRepository
import ro.jf.bk.account.service.domain.port.AccountService
import ro.jf.bk.account.service.domain.port.TransactionRepository
import ro.jf.bk.account.service.domain.port.TransactionService
import ro.jf.bk.account.service.domain.service.AccountServiceImpl
import ro.jf.bk.account.service.domain.service.TransactionServiceImpl
import java.sql.DriverManager
import javax.sql.DataSource

fun Application.configureDependencies() {
    install(Koin) {
        modules(modules = module {
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
            single<AccountRepository> { AccountExposedRepository(get()) }
            single<AccountService> { AccountServiceImpl(get()) }
            single<TransactionRepository> { TransactionExposedRepository(get()) }
            single<TransactionService> { TransactionServiceImpl(get()) }
        })
    }
}
