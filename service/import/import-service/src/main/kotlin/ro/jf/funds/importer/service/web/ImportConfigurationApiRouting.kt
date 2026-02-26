package ro.jf.funds.importer.service.web

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.importer.api.model.*
import ro.jf.funds.importer.service.domain.UpdateImportConfigurationCommand
import ro.jf.funds.importer.service.service.ImportConfigurationService
import ro.jf.funds.importer.service.web.mapper.toImportMatchers
import ro.jf.funds.importer.service.web.mapper.toTO
import ro.jf.funds.platform.api.model.PageTO
import ro.jf.funds.platform.jvm.web.pageRequest
import ro.jf.funds.platform.jvm.web.sortRequest
import ro.jf.funds.platform.jvm.web.userId
import java.util.*

private val log = logger { }

fun Routing.importConfigurationApiRouting(
    importConfigurationService: ImportConfigurationService,
) {
    route("/funds-api/import/v1/import-configurations") {
        post {
            val userId = call.userId()
            val request = call.receive<CreateImportConfigurationRequest>()
            log.info { "Create import configuration for user $userId, name ${request.name}." }
            val configuration = importConfigurationService.createImportConfiguration(
                userId,
                request.name,
                toImportMatchers(request.accountMatchers, request.fundMatchers, request.exchangeMatchers, request.labelMatchers),
            )
            call.respond(HttpStatusCode.Created, configuration.toTO())
        }

        get {
            val userId = call.userId()
            val pageRequest = call.pageRequest()
            val sortRequest = call.sortRequest<ImportConfigurationSortField>()
            log.info { "List import configurations for user $userId." }
            val result = importConfigurationService.listImportConfigurations(userId, pageRequest, sortRequest)
            call.respond(HttpStatusCode.OK, PageTO(result.items.map { it.toTO() }, result.total))
        }

        get("/{importConfigurationId}") {
            val userId = call.userId()
            val importConfigurationId = UUID.fromString(call.parameters["importConfigurationId"])
            log.info { "Get import configuration $importConfigurationId for user $userId." }
            val configuration = importConfigurationService.getImportConfiguration(userId, importConfigurationId)
            if (configuration != null) {
                call.respond(HttpStatusCode.OK, configuration.toTO())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        put("/{importConfigurationId}") {
            val userId = call.userId()
            val importConfigurationId = UUID.fromString(call.parameters["importConfigurationId"])
            val request = call.receive<UpdateImportConfigurationRequest>()
            log.info { "Update import configuration $importConfigurationId for user $userId." }
            val matchers = if (request.accountMatchers != null || request.fundMatchers != null ||
                request.exchangeMatchers != null || request.labelMatchers != null
            ) {
                toImportMatchers(
                    request.accountMatchers ?: emptyList(),
                    request.fundMatchers ?: emptyList(),
                    request.exchangeMatchers ?: emptyList(),
                    request.labelMatchers ?: emptyList(),
                )
            } else {
                null
            }
            val configuration = importConfigurationService.updateImportConfiguration(
                userId,
                importConfigurationId,
                UpdateImportConfigurationCommand(name = request.name, matchers = matchers),
            )
            if (configuration != null) {
                call.respond(HttpStatusCode.OK, configuration.toTO())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        delete("/{importConfigurationId}") {
            val userId = call.userId()
            val importConfigurationId = UUID.fromString(call.parameters["importConfigurationId"])
            log.info { "Delete import configuration $importConfigurationId for user $userId." }
            val deleted = importConfigurationService.deleteImportConfiguration(userId, importConfigurationId)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
