package ro.jf.funds.fund.service.web

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.toListTO
import ro.jf.funds.commons.web.userId
import ro.jf.funds.fund.api.model.CreateFundTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.service.domain.Fund
import ro.jf.funds.fund.service.mapper.toTO
import java.util.*

private val log = logger { }

fun Routing.fundApiRouting(fundService: ro.jf.funds.fund.service.service.FundService) {
    route("/funds-api/fund/v1/funds") {
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
        get("/name/{name}") {
            val userId = call.userId()
            val name = call.parameters["name"]?.let(::FundName) ?: error("Name is missing.")
            log.debug { "Get account by name $name for user id $userId." }
            val fund = fundService.findByName(userId, name)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(status = HttpStatusCode.OK, message = fund.toTO())
        }
        post {
            val userId = call.userId()
            val request = call.receive<CreateFundTO>()
            log.info { "Create fund $request for user $userId." }
            if (fundService.findByName(userId, request.name) != null)
                return@post call.respond(HttpStatusCode.Conflict)
            val fund = fundService.createFund(userId, request)
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
