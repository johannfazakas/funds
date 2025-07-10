package ro.jf.funds.importer.service.service


import mu.KotlinLogging.logger
import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.event.Producer
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportTaskTO
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
    suspend fun startImport(userId: UUID, configuration: ImportConfigurationTO, files: List<String>): ImportTaskTO =
        withSuspendingSpan {
            log.info { "Importing files >> user = $userId configuration = $configuration files count = ${files.size}." }
            val importTask = importTaskRepository.save(userId, ImportTaskTO.Status.IN_PROGRESS)
            try {
                // TODO(Johann-34) what if I would parallelize by file? And then merge the results on the task level?
                val importItems = importParserRegistry[configuration.fileType].parse(configuration, files)
                val fundTransactions = importFundConversionService.mapToFundRequest(userId, importItems)
                createFundTransactionsProducer.send(Event(userId, fundTransactions, importTask.taskId))
                importTask
            } catch (e: Exception) {
                log.warn(e) { "Error while importing files >> user = $userId configuration = $configuration files = $files." }
                importTaskRepository
                    .update(userId, importTask.copy(status = ImportTaskTO.Status.FAILED, reason = e.message?.take(255)))
            }
        }

    suspend fun getImport(userId: UUID, taskId: UUID): ImportTaskTO? = withSuspendingSpan {
        importTaskRepository.findById(userId, taskId)
    }

    suspend fun completeImport(userId: UUID, taskId: UUID) = withSuspendingSpan {
        importTaskRepository.update(userId, ImportTaskTO(taskId, ImportTaskTO.Status.COMPLETED))
    }

    suspend fun failImport(userId: UUID, taskId: UUID, reason: String?) = withSuspendingSpan {
        importTaskRepository.update(userId, ImportTaskTO(taskId, ImportTaskTO.Status.FAILED, reason?.take(100)))
    }
}
