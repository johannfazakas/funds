package ro.jf.funds.importer.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.funds.importer.service.service.ImportFileService
import ro.jf.funds.importer.service.service.ImportService
import ro.jf.funds.importer.service.web.importApiRouting
import ro.jf.funds.importer.service.web.importFileApiRouting

fun Application.configureImportRouting() {
    routing {
        importApiRouting(get<ImportService>())
        importFileApiRouting(get<ImportFileService>())
    }
}
