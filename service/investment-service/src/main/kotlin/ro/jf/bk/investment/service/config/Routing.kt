package ro.jf.bk.investment.service.config

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import ro.jf.bk.investment.api.model.InvestmentPosition
import ro.jf.bk.investment.api.model.ListTO

fun Application.configureRouting() {
    routing {
        route("/api/investment") {
            get("/instruments/{symbol}/positions") {
                val symbol = call.parameters["symbol"] ?: error("Symbol is required")
                val someDay = LocalDate(2024, 12, 1)
                val positions = ListTO(listOf(InvestmentPosition(symbol, someDay, someDay), InvestmentPosition(symbol, someDay, someDay)))
                call.respond(positions)
            }
        }
    }
}
