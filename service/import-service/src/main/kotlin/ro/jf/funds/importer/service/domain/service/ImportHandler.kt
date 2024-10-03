package ro.jf.funds.importer.service.domain.service

import mu.KotlinLogging.logger
import ro.jf.funds.importer.service.domain.model.ImportItem
import java.util.*

private val log = logger { }

class ImportHandler {
    fun import(userId: UUID, importItems: List<ImportItem>) {
        log.info { "Handling import >> user = $userId items size = ${importItems.size}." }
    }
}
