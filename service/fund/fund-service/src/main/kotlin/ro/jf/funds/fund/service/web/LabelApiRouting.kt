package ro.jf.funds.fund.service.web

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.fund.api.model.CreateLabelTO
import ro.jf.funds.fund.service.mapper.toTO
import ro.jf.funds.platform.jvm.web.userId
import java.util.*

private val log = logger { }

fun Routing.labelApiRouting(labelService: ro.jf.funds.fund.service.service.LabelService) {
    route("/funds-api/fund/v1/labels") {
        get {
            val userId = call.userId()
            log.debug { "List labels for user $userId." }
            val labels = labelService.listLabels(userId)
            call.respond(labels.map { it.toTO() })
        }
        post {
            val userId = call.userId()
            val request = call.receive<CreateLabelTO>()
            log.info { "Create label $request for user $userId." }
            val label = labelService.createLabel(userId, request)
            call.respond(status = HttpStatusCode.Created, message = label.toTO())
        }
        delete("/{labelId}") {
            val userId = call.userId()
            val labelId = call.parameters["labelId"]?.let(UUID::fromString) ?: error("Label id is missing.")
            log.info { "Delete label by id $labelId from user $userId." }
            labelService.deleteLabel(userId, labelId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
