package ro.jf.funds.importer.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.funds.importer.service.service.ImportService
import ro.jf.funds.importer.service.web.importApiRouting

fun Application.configureImportRouting() {
    routing {
        importApiRouting(get<ImportService>())
    }
}
