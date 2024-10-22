package ro.jf.funds.user.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.funds.user.service.adapter.web.userApiRouting
import ro.jf.funds.user.service.domain.port.UserRepository

fun Application.configureRouting() {
    routing {
        userApiRouting(get<UserRepository>())
    }
}
