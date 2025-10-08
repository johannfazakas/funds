package ro.jf.funds.fund.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.funds.fund.service.service.AccountService
import ro.jf.funds.fund.service.service.FundService
import ro.jf.funds.fund.service.service.TransactionService
import ro.jf.funds.fund.service.web.accountApiRouting
import ro.jf.funds.fund.service.web.fundApiRouting
import ro.jf.funds.fund.service.web.fundTransactionApiRouting

fun Application.configureFundRouting() {
    routing {
        accountApiRouting(get<AccountService>())
        fundApiRouting(get<FundService>())
        fundTransactionApiRouting(get<TransactionService>())
    }
}
