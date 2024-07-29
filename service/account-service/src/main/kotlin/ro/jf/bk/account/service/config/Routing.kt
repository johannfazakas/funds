package ro.jf.bk.account.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.bk.account.service.adapter.web.accountApiRouting
import ro.jf.bk.account.service.adapter.web.transactionApiRouting
import ro.jf.bk.account.service.domain.port.AccountService
import ro.jf.bk.account.service.domain.port.TransactionService

fun Application.configureRouting() {
    routing {
        accountApiRouting(get<AccountService>())
        transactionApiRouting(get<TransactionService>())
    }
}
