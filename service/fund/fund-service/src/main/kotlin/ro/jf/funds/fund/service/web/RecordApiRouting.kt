package ro.jf.funds.fund.service.web

import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.fund.api.model.RecordSortField
import ro.jf.funds.fund.service.domain.Record
import ro.jf.funds.fund.service.domain.RecordFilter
import ro.jf.funds.fund.service.mapper.toTO
import ro.jf.funds.fund.service.service.RecordService
import ro.jf.funds.platform.api.model.PageTO
import ro.jf.funds.platform.jvm.web.pageRequest
import ro.jf.funds.platform.jvm.web.sortRequest
import ro.jf.funds.platform.jvm.web.userId
import java.util.*

private val log = logger { }

fun Routing.recordApiRouting(recordService: RecordService) {
    route("/funds-api/fund/v1/records") {
        get {
            val userId = call.userId()
            val pageRequest = call.pageRequest()
            val sortRequest = call.sortRequest<RecordSortField>()
            val filter = RecordFilter(
                accountId = call.request.queryParameters["accountId"]?.let(UUID::fromString),
                fundId = call.request.queryParameters["fundId"]?.let(UUID::fromString),
                unit = call.request.queryParameters["unit"],
                label = call.request.queryParameters["label"],
                fromDate = call.request.queryParameters["fromDate"]?.let(LocalDate::parse),
                toDate = call.request.queryParameters["toDate"]?.let(LocalDate::parse),
            )
            log.debug { "List records for user $userId with filter $filter." }
            val result = recordService.listRecords(userId, filter, pageRequest, sortRequest)
            call.respond(PageTO(result.items.map(Record::toTO), result.total))
        }
    }
}
