package ro.jf.bk.fund.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.bk.fund.service.service.FundService
import ro.jf.bk.fund.service.service.FundTransactionService
import ro.jf.bk.fund.service.web.fundApiRouting
import ro.jf.bk.fund.service.web.fundTransactionApiRouting

fun Application.configureRouting() {
    routing {
        fundApiRouting(get<FundService>())
        fundTransactionApiRouting(get<FundTransactionService>())
    }
}
