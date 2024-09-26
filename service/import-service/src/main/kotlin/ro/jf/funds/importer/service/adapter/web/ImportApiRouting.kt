package ro.jf.funds.importer.service.adapter.web

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.bk.commons.service.routing.userId
import ro.jf.funds.importer.api.CSV_DELIMITER_HEADER
import ro.jf.funds.importer.api.CSV_ENCODING_HEADER
import ro.jf.funds.importer.api.model.CsvFormattingTO
import ro.jf.funds.importer.api.model.ImportResponse
import ro.jf.funds.importer.service.adapter.file.CsvParser

private val log = logger { }

fun Routing.importApiRouting(
    csvParser: CsvParser
) {
    route("/bk-api/import/v1/imports") {
        post {
            val userId = call.userId()
            log.info { "Import request for user $userId." }

            val csvParts = call
                .receiveMultipart()
                .readAllParts()
                .mapNotNull { it.csvContent() }
            log.info { "Import request for user $userId with ${csvParts.size} csv parts." }

            if (csvParts.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "No CSV file uploaded.")
            } else {
                val csvLines = csvParser.parseCsv(csvParts, call.request.csvFormatting())
                // Process the CSV content (csvContent is a String containing the CSV data)
                call.respond(HttpStatusCode.OK, ImportResponse("Imported in service"))
            }
        }
    }
}

private fun PartData.csvContent(): String? {
    val part = (this as? PartData.FileItem)
        ?.takeIf { it.isCsv() }
        ?.let { String(it.streamProvider().readBytes()) }
    this.dispose() // Dispose the part after use
    return part
}

private fun PartData.FileItem.isCsv(): Boolean {
    return this.originalFileName?.endsWith(".csv") == true
}

private fun ApplicationRequest.csvFormatting(): CsvFormattingTO {
    val defaultFormatting = CsvFormattingTO()
    return CsvFormattingTO(
        encoding = this.headers[CSV_ENCODING_HEADER] ?: defaultFormatting.encoding,
        delimiter = this.headers[CSV_DELIMITER_HEADER] ?: defaultFormatting.delimiter,
    )
}
