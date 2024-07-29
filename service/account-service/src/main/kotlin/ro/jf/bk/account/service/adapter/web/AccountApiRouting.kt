package ro.jf.bk.account.service.adapter.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.bk.account.api.model.CreateCurrencyAccountTO
import ro.jf.bk.account.api.model.CreateInstrumentAccountTO
import ro.jf.bk.account.service.adapter.mapper.toCommand
import ro.jf.bk.account.service.adapter.mapper.toTO
import ro.jf.bk.account.service.domain.model.Account
import ro.jf.bk.account.service.domain.port.AccountService
import ro.jf.bk.commons.model.toListTO
import ro.jf.bk.commons.web.USER_ID_HEADER
import java.util.*

private val log = logger { }

fun Routing.accountApiRouting(accountService: AccountService) {
    route("/bk-api/account/v1/accounts") {
        get {
            val userId = call.userId()
            log.debug { "List all accounts by user id $userId." }
            val accounts = accountService.listAccounts(userId)
            call.respond(accounts.toListTO(Account::toTO))
        }
        get("/{accountId}") {
            val userId = call.userId()
            val accountId = call.parameters["accountId"]?.let(UUID::fromString) ?: error("Account id is missing.")
            log.debug { "Get account by id $accountId for user id $userId." }
            val account = accountService.findAccountById(userId, accountId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(account.toTO())
        }
        post("/currency") {
            val userId = call.userId()
            val request = call.receive<CreateCurrencyAccountTO>()
            log.info { "Create currency account $request for user $userId." }
            if (accountService.findAccountByName(userId, request.name) != null)
                return@post call.respond(HttpStatusCode.Conflict)
            val account = accountService.createAccount(userId, request.toCommand(userId))
            call.respond(status = HttpStatusCode.Created, message = account.toTO())
        }
        post("/instrument") {
            val userId = call.userId()
            val request = call.receive<CreateInstrumentAccountTO>()
            log.info { "Create instrument account $request for user $userId." }
            if (accountService.findAccountByName(userId, request.name) != null)
                return@post call.respond(HttpStatusCode.Conflict)
            val account = accountService.createAccount(userId, request.toCommand(userId))
            call.respond(status = HttpStatusCode.Created, message = account.toTO())
        }
        delete("/{accountId}") {
            val userId = call.userId()
            val accountId = call.parameters["accountId"]?.let(UUID::fromString) ?: error("Account id is missing.")
            log.info { "Delete account by id $accountId from user $userId." }
            accountService.deleteAccount(userId, accountId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ApplicationCall.userId(): UUID {
    return request.headers[USER_ID_HEADER]?.let(UUID::fromString) ?: error("User id is missing.")
}