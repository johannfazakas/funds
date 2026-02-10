package ro.jf.funds.fund.service.web

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.platform.api.model.PageTO
import ro.jf.funds.platform.jvm.web.pageRequest
import ro.jf.funds.platform.jvm.web.sortRequest
import ro.jf.funds.platform.jvm.web.userId
import ro.jf.funds.fund.api.model.AccountSortField
import ro.jf.funds.fund.api.model.CreateAccountTO
import ro.jf.funds.fund.service.domain.Account
import ro.jf.funds.fund.service.mapper.toTO
import ro.jf.funds.fund.service.service.AccountService
import java.util.*

private val log = logger { }

fun Routing.accountApiRouting(accountService: AccountService) {
    route("/funds-api/fund/v1/accounts") {
        get {
            val userId = call.userId()
            val pageRequest = call.pageRequest()
            val sortRequest = call.sortRequest<AccountSortField>()
            log.debug { "List all accounts by user id $userId." }
            val result = accountService.listAccounts(userId, pageRequest, sortRequest)
            call.respond(PageTO(result.items.map(Account::toTO), result.total))
        }

        get("/{id}") {
            val userId = call.userId()
            val accountId = call.parameters["id"]?.let(UUID::fromString) ?: error("Account id is missing.")
            log.debug { "Get account by id $accountId for user id $userId." }
            val account = accountService.findAccountById(userId, accountId)
            call.respond(HttpStatusCode.OK, account.toTO())
        }

        post {
            val userId = call.userId()
            val request = call.receive<CreateAccountTO>()
            log.info { "Create account $request for user $userId." }
            val account = accountService.createAccount(userId, request)
            call.respond(HttpStatusCode.Created, account.toTO())
        }

        delete("/{id}") {
            val userId = call.userId()
            val accountId = call.parameters["id"]?.let(UUID::fromString) ?: error("Account id is missing.")
            log.info { "Delete account by id $accountId from user $userId." }
            accountService.deleteAccount(userId, accountId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}