package ro.jf.funds.importer.service.adapter.web

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.bk.commons.service.routing.userId
import ro.jf.funds.importer.api.model.ImportResponse

private val log = logger { }

fun Routing.importApiRouting() {
    route("/bk-api/import/v1/imports") {
        post {
            val userId = call.userId()
            log.info { "Import request for user $userId." }

            val csvParts = call
                .receiveMultipart()
                .readAllParts()
                .mapNotNull { it.extractCsvContent() }
            log.info { "Import request for user $userId with ${csvParts.size} csv parts." }

            if (csvParts.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "No CSV file uploaded.")
            } else {
                // Process the CSV content (csvContent is a String containing the CSV data)
                call.respond(HttpStatusCode.OK, ImportResponse("Imported in service"))
            }
        }
    }
}

private fun PartData.extractCsvContent(): String? {
    val part = (this as? PartData.FileItem)
        ?.takeIf { it.isCsv() }
        ?.let { String(it.streamProvider().readBytes()) }
    this.dispose() // Dispose the part after use
    return part
}

private fun PartData.FileItem.isCsv(): Boolean {
    return this.originalFileName?.endsWith(".csv") == true
}
