package ro.jf.funds.importer.service.adapter.web

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging.logger
import ro.jf.bk.commons.service.routing.userId
import ro.jf.funds.importer.api.CSV_DELIMITER_HEADER
import ro.jf.funds.importer.api.CSV_ENCODING_HEADER
import ro.jf.funds.importer.api.model.CsvFormattingTO
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportResponse
import ro.jf.funds.importer.service.adapter.file.CsvParser
import ro.jf.funds.importer.service.adapter.mapper.toImportItem
import ro.jf.funds.importer.service.domain.model.ImportConfiguration
import ro.jf.funds.importer.service.domain.port.ImportService

private val log = logger { }

fun Routing.importApiRouting(
    csvParser: CsvParser,
    importService: ImportService
) {
    route("/bk-api/import/v1/imports") {
        post {
            val userId = call.userId()
            log.info { "Import request for user $userId." }

            val requestParts = call
                .receiveMultipart()
                .readAllParts()
            val rawCsvParts = requestParts.rawCsvParts()
            log.info { "Import request for user $userId with ${rawCsvParts.size} csv parts." }

            val importConfiguration = requestParts.importConfigurationPart()

            if (rawCsvParts.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "No CSV file uploaded.")
            } else if (importConfiguration == null) {
                call.respond(HttpStatusCode.BadRequest, "No import configuration provided.")
            } else {
                val csvLines = csvParser.parseCsv(rawCsvParts, call.request.csvFormatting())
                importService.import(
                    userId,
                    ImportConfiguration(accounts = listOf()),
                    csvLines.map { it.toImportItem(importConfiguration) })
                // Process the CSV content (csvContent is a String containing the CSV data)
                call.respond(HttpStatusCode.OK, ImportResponse("Imported in service"))
            }
        }
    }
}

private fun List<PartData>.importConfigurationPart(): ImportConfigurationTO? {
    return this
        .mapNotNull { it as? PartData.FormItem }
        .firstOrNull { it.name == "configuration" }
        ?.value
        ?.let { json -> Json.decodeFromString<ImportConfigurationTO>(json) }
}

private fun List<PartData>.rawCsvParts(): List<String> {
    return this
        .mapNotNull { it as? PartData.FileItem }
        .filter { it.isCsv() }
        .map { filePart -> String(filePart.streamProvider().readBytes()).also { filePart.dispose() } }
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
