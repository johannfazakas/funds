package ro.jf.funds.importer.service.adapter.web

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.bk.commons.service.routing.userId
import ro.jf.funds.importer.api.model.ImportRequest
import ro.jf.funds.importer.api.model.ImportResponse

private val log = logger { }

fun Routing.importApiRouting() {
    route("/bk-api/import/v1/imports") {
        post {
            val userId = call.userId()
            val request = call.receive<ImportRequest>()
            log.info { "Import request for user $userId - $request." }
            call.respond(ImportResponse("Imported in service"))
        }
    }
}