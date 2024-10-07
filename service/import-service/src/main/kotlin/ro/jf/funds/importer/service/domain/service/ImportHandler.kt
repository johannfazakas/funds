package ro.jf.funds.importer.service.domain.service

import mu.KotlinLogging.logger
import ro.jf.funds.importer.service.domain.model.ImportTransaction
import java.util.*

private val log = logger { }

class ImportHandler {
    fun import(userId: UUID, importItems: List<ImportTransaction>) {
        log.info { "Handling import >> user = $userId items size = ${importItems.size}." }
    }
}
