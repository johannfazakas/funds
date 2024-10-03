package ro.jf.funds.importer.service.domain.service

import mu.KotlinLogging.logger
import ro.jf.funds.importer.service.domain.model.ImportConfiguration
import ro.jf.funds.importer.service.domain.port.ImportService
import ro.jf.funds.importer.service.domain.service.parser.ImportParserRegistry
import java.util.*

private val log = logger { }

class ImportServiceImpl(
    private val importParserRegistry: ImportParserRegistry,
    private val importHandler: ImportHandler
) : ImportService {
    override suspend fun import(userId: UUID, configuration: ImportConfiguration, files: List<String>) {
        log.info { "Importing files >> user = $userId configuration = $configuration files count = ${files.size}." }
        val importItems = importParserRegistry[configuration.importType].parse(files)
        importHandler.import(userId, importItems)
    }
}
