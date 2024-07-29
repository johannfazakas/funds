package ro.jf.bk.user.service

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.get
import ro.jf.bk.user.service.config.configureDependencies
import ro.jf.bk.user.service.config.configureRouting
import ro.jf.bk.user.service.config.configureSerialization
import ro.jf.bk.user.service.config.migrateDatabase
import javax.sql.DataSource

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencies()
    configureSerialization()
    migrateDatabase(get<DataSource>())
    configureRouting()
}
