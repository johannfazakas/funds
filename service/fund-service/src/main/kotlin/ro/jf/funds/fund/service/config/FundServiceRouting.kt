package ro.jf.funds.fund.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.funds.fund.service.service.FundTransactionService
import ro.jf.funds.fund.service.web.fundApiRouting
import ro.jf.funds.fund.service.web.fundTransactionApiRouting

fun Application.configureRouting() {
    routing {
        fundApiRouting(get<ro.jf.funds.fund.service.service.FundService>())
        fundTransactionApiRouting(get<FundTransactionService>())
    }
}
