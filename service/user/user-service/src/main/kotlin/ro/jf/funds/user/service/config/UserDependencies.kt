package ro.jf.funds.user.service.config

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import ro.jf.funds.commons.persistence.getDataSource
import ro.jf.funds.commons.persistence.getDbConnection
import ro.jf.funds.user.service.adapter.persistence.UserExposedRepository
import ro.jf.funds.user.service.domain.port.UserRepository
import javax.sql.DataSource

val Application.userDependencies
    get() = module {
        single<DataSource> { environment.getDataSource() }
        single<Database> { Database.connect(datasource = get()) }
        single { environment.getDbConnection() }
        single<UserRepository> { UserExposedRepository(get()) }
    }
