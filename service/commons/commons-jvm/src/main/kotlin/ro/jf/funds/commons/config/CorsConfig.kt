package ro.jf.funds.commons.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import ro.jf.funds.commons.web.USER_ID_HEADER

fun Application.configureCors() {
    install(CORS) {
        anyHost()
        anyMethod()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(USER_ID_HEADER)
        allowCredentials = true
    }
}
