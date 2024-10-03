package ro.jf.funds.importer.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.funds.importer.service.domain.service.parser.ImportParserRegistry
import ro.jf.funds.importer.service.adapter.web.importApiRouting
import ro.jf.funds.importer.service.domain.port.ImportService

// TODO(Johann) should the rest of routing configs be renamed to importServiceRouting? or could it be extracted as the dependencies?
fun Application.configureRouting() {
    routing {
        importApiRouting(get<ImportService>())
    }
}
