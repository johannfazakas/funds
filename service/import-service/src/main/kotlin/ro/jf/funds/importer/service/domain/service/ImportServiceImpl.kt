package ro.jf.funds.importer.service.domain.service

import mu.KotlinLogging.logger
import ro.jf.funds.importer.service.domain.model.ImportConfiguration
import ro.jf.funds.importer.service.domain.model.ImportItem
import ro.jf.funds.importer.service.domain.port.ImportService
import java.util.*

private val log = logger { }

class ImportServiceImpl : ImportService {
    override suspend fun import(userId: UUID, configuration: ImportConfiguration, importItems: List<ImportItem>) {
        log.info { "Importing for user $userId items ${importItems.size} with configuration $configuration." }
    }
}
