package ro.jf.funds.user.service.config

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import ro.jf.funds.commons.persistence.getDataSource
import ro.jf.funds.user.service.persistence.UserRepository
import ro.jf.funds.user.service.service.UserService
import javax.sql.DataSource

val Application.userDependencies
    get() = module {
        single<DataSource> { environment.getDataSource() }
        single<Database> { Database.connect(datasource = get()) }
        single { UserRepository(get()) }
        single { UserService(get()) }
    }
