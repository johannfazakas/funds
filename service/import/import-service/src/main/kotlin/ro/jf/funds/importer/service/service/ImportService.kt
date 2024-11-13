package ro.jf.funds.importer.service.service


import mu.KotlinLogging.logger
import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.event.Producer
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportTaskTO
import ro.jf.funds.importer.service.persistence.ImportTaskRepository
import ro.jf.funds.importer.service.service.parser.ImportParserRegistry
import java.util.*

private val log = logger { }

class ImportService(
    private val importTaskRepository: ImportTaskRepository,
    private val importParserRegistry: ImportParserRegistry,
    private val importFundMapper: ImportFundMapper,
    private val createFundTransactionsProducer: Producer<CreateFundTransactionsTO>
) {
    suspend fun startImport(userId: UUID, configuration: ImportConfigurationTO, files: List<String>): ImportTaskTO {
        log.info { "Importing files >> user = $userId configuration = $configuration files count = ${files.size}." }
        val importTask = importTaskRepository.save(userId, ImportTaskTO.Status.IN_PROGRESS)
        return try {
            val importItems = importParserRegistry[configuration.fileType].parse(configuration, files)
            val fundTransactions = importFundMapper.mapToFundTransactions(userId, importTask.taskId, importItems)
            createFundTransactionsProducer
                .send(Event(userId, CreateFundTransactionsTO(fundTransactions), importTask.taskId))
            importTask
        } catch (e: Exception) {
            log.warn(e) { "Error while importing files >> user = $userId configuration = $configuration files = $files." }
            importTaskRepository
                .update(userId, importTask.copy(status = ImportTaskTO.Status.FAILED, reason = e.message))
        }
    }

    suspend fun getImportStatus(userId: UUID, taskId: UUID): ImportTaskTO? {
        return importTaskRepository.findById(userId, taskId)
    }
}
