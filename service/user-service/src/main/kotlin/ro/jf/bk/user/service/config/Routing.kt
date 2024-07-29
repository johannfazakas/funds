package ro.jf.bk.user.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.bk.user.service.adapter.web.userApiRouting
import ro.jf.bk.user.service.domain.port.UserRepository

fun Application.configureRouting() {
    routing {
        userApiRouting(get<UserRepository>())
    }
}
