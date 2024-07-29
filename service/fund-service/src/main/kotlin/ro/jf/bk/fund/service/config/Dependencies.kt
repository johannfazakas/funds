package ro.jf.bk.fund.service.config

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.postgresql.ds.PGSimpleDataSource
import ro.jf.bk.fund.service.adapter.persistence.FundExposedRepository
import ro.jf.bk.fund.service.domain.port.FundRepository
import ro.jf.bk.fund.service.domain.port.FundService
import ro.jf.bk.fund.service.domain.service.FundServiceImpl
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
            single<FundRepository> { FundExposedRepository(get()) }
            single<FundService> { FundServiceImpl(get()) }
        })
    }
}
