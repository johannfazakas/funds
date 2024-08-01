package ro.jf.bk.fund.service.adapter.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.bk.commons.model.toListTO
import ro.jf.bk.commons.web.USER_ID_HEADER
import ro.jf.bk.fund.api.model.CreateFundTO
import ro.jf.bk.fund.service.adapter.mapper.toCommand
import ro.jf.bk.fund.service.adapter.mapper.toTO
import ro.jf.bk.fund.service.domain.model.Fund
import ro.jf.bk.fund.service.domain.port.FundService
import java.util.*

private val log = logger { }

fun Routing.fundApiRouting(fundService: FundService) {
    route("/bk-api/fund/v1/funds") {
        get {
            val userId = call.userId()
            log.debug { "List all accounts by user id $userId." }
            val funds = fundService.listFunds(userId)
            call.respond(funds.toListTO(Fund::toTO))
        }
        get("/{fundId}") {
            val userId = call.userId()
            val fundId = call.parameters["fundId"]?.let(UUID::fromString) ?: error("Fund id is missing.")
            log.debug { "Get account by id $fundId for user id $userId." }
            val fund = fundService.findById(userId, fundId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(status = HttpStatusCode.OK, message = fund.toTO())
        }
        post {
            val userId = call.userId()
            val request = call.receive<CreateFundTO>()
            log.info { "Create currency account $request for user $userId." }
            if (fundService.findByName(userId, request.name) != null)
                return@post call.respond(HttpStatusCode.Conflict)
            val fund = fundService.createFund(request.toCommand(userId))
            call.respond(status = HttpStatusCode.Created, message = fund.toTO())
        }
        delete("/{fundId}") {
            val userId = call.userId()
            val fundId = call.parameters["fundId"]?.let(UUID::fromString) ?: error("Fund id is missing.")
            log.info { "Delete account by id $fundId from user $userId." }
            fundService.deleteFund(userId, fundId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ApplicationCall.userId(): UUID {
    return request.headers[USER_ID_HEADER]?.let(UUID::fromString) ?: error("User id is missing.")
}
