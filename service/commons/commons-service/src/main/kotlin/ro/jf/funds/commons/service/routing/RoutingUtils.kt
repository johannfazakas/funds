package ro.jf.funds.commons.service.routing

import io.ktor.server.application.*
import ro.jf.funds.commons.web.USER_ID_HEADER
import java.util.*

fun ApplicationCall.userId(): UUID {
    return request.headers[USER_ID_HEADER]?.let(UUID::fromString) ?: error("User id is missing.")
}
