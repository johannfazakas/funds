package ro.jf.funds.platform.jvm.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import ro.jf.funds.platform.jvm.web.USER_ID_HEADER

fun Application.configureCors() {
    install(CORS) {
        anyHost()
        anyMethod()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(USER_ID_HEADER)
        allowCredentials = true
    }
}
