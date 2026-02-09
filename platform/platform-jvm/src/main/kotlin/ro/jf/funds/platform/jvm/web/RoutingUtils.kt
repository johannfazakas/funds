package ro.jf.funds.platform.jvm.web

import io.ktor.server.application.*
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.SortField
import ro.jf.funds.platform.api.model.SortOrder
import ro.jf.funds.platform.api.model.SortRequest
import java.util.*

fun ApplicationCall.userId(): UUID {
    return request.headers[USER_ID_HEADER]?.let(UUID::fromString) ?: error("User id is missing.")
}

fun ApplicationCall.pageRequest(): PageRequest? {
    val offset = request.queryParameters["offset"]?.toIntOrNull()
    val limit = request.queryParameters["limit"]?.toIntOrNull()
    return if (offset != null && limit != null) PageRequest(offset, limit) else null
}

inline fun <reified T> ApplicationCall.sortRequest(): SortRequest<T>? where T : Enum<T>, T : SortField {
    val fieldName = request.queryParameters["sort"]?.uppercase() ?: return null
    val field = runCatching { enumValueOf<T>(fieldName) }.getOrNull() ?: return null
    val order = request.queryParameters["order"]?.uppercase()?.let {
        runCatching { SortOrder.valueOf(it) }.getOrNull()
    } ?: SortOrder.ASC
    return SortRequest(field, order)
}
