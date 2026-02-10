package ro.jf.funds.platform.jvm.client

import io.ktor.http.*
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.SortField
import ro.jf.funds.platform.api.model.SortRequest

fun ParametersBuilder.appendPageRequest(pageRequest: PageRequest?) {
    pageRequest?.let {
        append("offset", it.offset.toString())
        append("limit", it.limit.toString())
    }
}

fun <T> ParametersBuilder.appendSortRequest(sortRequest: SortRequest<T>?) where T : Enum<T>, T : SortField {
    sortRequest?.let {
        append("sort", it.field.name.lowercase())
        append("order", it.order.name.lowercase())
    }
}
