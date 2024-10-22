package ro.jf.funds.account.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.funds.account.service.service.AccountService
import ro.jf.funds.account.service.service.AccountTransactionService
import ro.jf.funds.account.service.web.accountApiRouting
import ro.jf.funds.account.service.web.accountTransactionApiRouting

fun Application.configureRouting() {
    routing {
        accountApiRouting(get<AccountService>())
        accountTransactionApiRouting(get<AccountTransactionService>())
    }
}
