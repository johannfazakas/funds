package ro.jf.funds.importer.service.service


import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import mu.KotlinLogging.logger
import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.event.Producer
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.service.domain.ImportFile
import ro.jf.funds.importer.service.domain.ImportTask
import ro.jf.funds.importer.service.domain.StartImportTaskCommand
import ro.jf.funds.importer.service.domain.UpdateImportTaskPartCommand
import ro.jf.funds.importer.service.persistence.ImportTaskRepository
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService
import ro.jf.funds.importer.service.service.parser.ImportParserRegistry
import java.util.*

private val log = logger { }

class ImportService(
    private val importTaskRepository: ImportTaskRepository,
    private val importParserRegistry: ImportParserRegistry,
    private val importFundConversionService: ImportFundConversionService,
    private val createFundTransactionsProducer: Producer<CreateFundTransactionsTO>,
) {
    suspend fun startImport(userId: UUID, configuration: ImportConfigurationTO, files: List<ImportFile>): ImportTask =
        withSuspendingSpan {
            log.info { "Importing files >> user = $userId configuration = $configuration files count = ${files.size}." }
            val importTask =
                importTaskRepository.startImportTask(StartImportTaskCommand(userId, files.map { it.name }))
            try {
                coroutineScope {
                    files
                        .map { file -> launch { startFileImport(userId, importTask, file, configuration) } }
                        .joinAll()
                }
            } catch (exception: Exception) {
                log.warn { "Importing file parts failed >> exception = $exception" }
                return@withSuspendingSpan importTaskRepository.findImportTaskById(userId, importTask.taskId)
                    ?: error("Import task ${importTask.taskId} not found")
            }
            importTask
        }

    private suspend fun startFileImport(
        userId: UUID,
        importTask: ImportTask,
        file: ImportFile,
        configuration: ImportConfigurationTO,
    ) {
        val importTaskPart = importTask.findPartByName(file.name)
            ?: error("Import file ${file.name} not found")
        try {
            val importItems =
                importParserRegistry[configuration.fileType].parse(configuration, listOf(file.content))
            val fundTransactions = importFundConversionService.mapToFundRequest(userId, importItems)
            createFundTransactionsProducer.send(Event(userId, fundTransactions, importTaskPart.taskPartId))
        } catch (e: Exception) {
            log.warn(e) { "Error while importing file >> user = $userId, fileName = ${file.name}, configuration = $configuration." }
            val failTaskPartCommand = UpdateImportTaskPartCommand.failed(userId, importTaskPart.taskPartId, e.message)
            importTaskRepository.updateTaskPart(failTaskPartCommand)
            throw e
        }
    }

    suspend fun getImport(userId: UUID, taskId: UUID): ImportTask? = withSuspendingSpan {
        importTaskRepository.findImportTaskById(userId, taskId)
    }

    suspend fun completeImport(userId: UUID, taskId: UUID) = withSuspendingSpan {
        importTaskRepository.updateTaskPart(UpdateImportTaskPartCommand.completed(userId, taskId))
    }

    suspend fun failImport(userId: UUID, taskId: UUID, reason: String?) = withSuspendingSpan {
        importTaskRepository.updateTaskPart(UpdateImportTaskPartCommand.failed(userId, taskId, reason))
    }
}
