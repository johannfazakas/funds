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
    suspend fun import(userId: UUID, configuration: ImportConfigurationTO, files: List<String>) {
        log.info { "Importing files >> user = $userId configuration = $configuration files count = ${files.size}." }
        val importTask = importTaskRepository.save(userId, ImportTaskTO.Status.IN_PROGRESS)
        val importItems = importParserRegistry[configuration.fileType].parse(configuration, files)
        val fundTransactions = importFundMapper.mapToFundTransactions(userId, importTask.taskId, importItems)
        createFundTransactionsProducer
            .send(Event(userId, CreateFundTransactionsTO(fundTransactions), importTask.taskId))
    }
}
