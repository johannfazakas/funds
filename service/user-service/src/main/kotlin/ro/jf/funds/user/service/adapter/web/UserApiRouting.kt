package ro.jf.funds.user.service.adapter.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.toListTO
import ro.jf.funds.user.api.model.CreateUserTO
import ro.jf.funds.user.service.adapter.web.mapper.toCommand
import ro.jf.funds.user.service.adapter.web.mapper.toTO
import ro.jf.funds.user.service.domain.port.UserRepository
import java.util.*

private val log = logger { }

fun Routing.userApiRouting(userRepository: UserRepository) {
    route("/bk-api/user/v1") {
        get("/users") {
            log.debug { "List all users." }
            val response = userRepository.listAll().map { it.toTO() }.toListTO()
            call.respond(response)
        }
        get("/users/{id}") {
            val userId = call.parameters["id"]
            log.debug { "Get user by id $userId." }
            val user = userId
                ?.let(UUID::fromString)
                ?.let { userRepository.findById(it) }
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(user.toTO())
        }
        get("/users/username/{username}") {
            val username = call.parameters["username"]
            log.debug { "Get user by username $username." }
            val user = username
                ?.let { userRepository.findByUsername(it) }
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(user.toTO())
        }
        post("/users") {
            val request = call.receive<CreateUserTO>()
            log.info { "Create user ${request.username}." }
            if (userRepository.findByUsername(request.username) != null)
                return@post call.respond(HttpStatusCode.Conflict)
            val user = userRepository.save(request.toCommand())
            call.respond(status = HttpStatusCode.Created, message = user.toTO())
        }
        delete("/users/{id}") {
            val userId = call.parameters["id"]
            log.info { "Delete user by id $userId." }
            val id = userId?.let(UUID::fromString)
                ?: return@delete call.respond(HttpStatusCode.NoContent)
            userRepository.deleteById(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
