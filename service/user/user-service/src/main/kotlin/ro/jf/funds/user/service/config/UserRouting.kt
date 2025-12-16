package ro.jf.funds.user.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.funds.user.service.service.UserService
import ro.jf.funds.user.service.web.userApiRouting

fun Application.configureUserRouting() {
    routing {
        userApiRouting(get<UserService>())
    }
}
