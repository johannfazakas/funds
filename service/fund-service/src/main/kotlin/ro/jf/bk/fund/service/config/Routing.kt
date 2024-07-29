package ro.jf.bk.fund.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.bk.fund.service.adapter.web.fundApiRouting
import ro.jf.bk.fund.service.domain.port.FundService

fun Application.configureRouting() {
    routing {
        fundApiRouting(get<FundService>())
    }
}
