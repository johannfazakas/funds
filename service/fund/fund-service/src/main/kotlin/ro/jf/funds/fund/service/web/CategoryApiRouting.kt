package ro.jf.funds.fund.service.web

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging.logger
import ro.jf.funds.fund.api.model.CreateCategoryTO
import ro.jf.funds.fund.service.mapper.toTO
import ro.jf.funds.platform.jvm.web.userId
import java.util.*

private val log = logger { }

fun Routing.categoryApiRouting(categoryService: ro.jf.funds.fund.service.service.CategoryService) {
    route("/funds-api/fund/v1/categories") {
        get {
            val userId = call.userId()
            log.debug { "List categories for user $userId." }
            val categories = categoryService.listCategories(userId)
            call.respond(categories.map { it.toTO() })
        }
        post {
            val userId = call.userId()
            val request = call.receive<CreateCategoryTO>()
            log.info { "Create category $request for user $userId." }
            val category = categoryService.createCategory(userId, request)
            call.respond(status = HttpStatusCode.Created, message = category.toTO())
        }
        delete("/{categoryId}") {
            val userId = call.userId()
            val categoryId = call.parameters["categoryId"]?.let(UUID::fromString) ?: error("Category id is missing.")
            log.info { "Delete category by id $categoryId from user $userId." }
            categoryService.deleteCategory(userId, categoryId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
