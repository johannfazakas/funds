package ro.jf.funds.platform.jvm.web

import io.ktor.server.application.*
import java.util.*

fun ApplicationCall.userId(): UUID {
    return request.headers[USER_ID_HEADER]?.let(UUID::fromString) ?: error("User id is missing.")
}
