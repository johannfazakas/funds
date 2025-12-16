package ro.jf.funds.user.service.web

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.commons.api.model.toListTO
import ro.jf.funds.user.api.model.CreateUserTO
import ro.jf.funds.user.service.service.UserService
import ro.jf.funds.user.service.web.mapper.toCommand
import ro.jf.funds.user.service.web.mapper.toTO
import java.util.*

private val log = logger { }

fun Routing.userApiRouting(userService: UserService) {
    route("/funds-api/user/v1") {
        get("/users") {
            log.debug { "List all users." }
            val response = userService.listAll().map { it.toTO() }.toListTO()
            call.respond(response)
        }
        get("/users/{id}") {
            val userId = call.parameters["id"]?.let(UUID::fromString)
                ?: throw IllegalArgumentException("Invalid user id")
            log.debug { "Get user by id $userId." }
            val user = userService.findById(userId)
            call.respond(user.toTO())
        }
        get("/users/username/{username}") {
            val username = call.parameters["username"]
            log.debug { "Get user by username $username." }
            val user = username
                ?.let { userService.findByUsername(it) }
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(user.toTO())
        }
        post("/users") {
            val request = call.receive<CreateUserTO>()
            log.info { "Create user ${request.username}." }
            val user = userService.createUser(request.toCommand())
            call.respond(status = HttpStatusCode.Created, message = user.toTO())
        }
        delete("/users/{id}") {
            val userId = call.parameters["id"]
            log.info { "Delete user by id $userId." }
            val id = userId?.let(UUID::fromString)
                ?: return@delete call.respond(HttpStatusCode.NoContent)
            userService.deleteById(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
