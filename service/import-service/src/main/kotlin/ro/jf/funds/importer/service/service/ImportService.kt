package ro.jf.funds.importer.service.service


import mu.KotlinLogging.logger
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.service.service.parser.ImportParserRegistry
import java.util.*

private val log = logger { }

class ImportService(
    private val importParserRegistry: ImportParserRegistry,
    private val importHandler: ImportHandler
) {
    suspend fun import(userId: UUID, configuration: ImportConfigurationTO, files: List<String>) {
        log.info { "Importing files >> user = $userId configuration = $configuration files count = ${files.size}." }
        val importItems = importParserRegistry[configuration.fileType].parse(configuration, files)
        importHandler.import(userId, importItems)
    }
}
