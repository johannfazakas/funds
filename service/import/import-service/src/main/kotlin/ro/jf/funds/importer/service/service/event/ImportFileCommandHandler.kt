package ro.jf.funds.importer.service.service.event

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.content.toByteArray
import com.benasher44.uuid.Uuid
import mu.KotlinLogging.logger
import ro.jf.funds.fund.api.model.CreateTransactionsTO
import ro.jf.funds.importer.api.model.ImportFileCommandTO
import ro.jf.funds.importer.service.config.S3Configuration
import ro.jf.funds.importer.service.domain.ImportConfiguration
import ro.jf.funds.importer.service.domain.ImportFile
import ro.jf.funds.importer.service.domain.ImportFileStatus
import ro.jf.funds.importer.service.domain.exception.ImportConfigurationNotFoundException
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.persistence.ImportFileRepository
import ro.jf.funds.importer.service.service.ImportConfigurationService
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService
import ro.jf.funds.importer.service.service.parser.ImportParserRegistry
import ro.jf.funds.platform.jvm.event.Event
import ro.jf.funds.platform.jvm.event.EventHandler
import ro.jf.funds.platform.jvm.event.Producer

private val log = logger { }

class ImportFileCommandHandler(
    private val importFileRepository: ImportFileRepository,
    private val importConfigurationService: ImportConfigurationService,
    private val importParserRegistry: ImportParserRegistry,
    private val importFundConversionService: ImportFundConversionService,
    private val createFundTransactionsProducer: Producer<CreateTransactionsTO>,
    private val s3Client: S3Client,
    private val s3Configuration: S3Configuration,
) : EventHandler<ImportFileCommandTO> {
    override suspend fun handle(event: Event<ImportFileCommandTO>) {
        val userId = event.userId
        val importFile = getImportingFile(userId, event.payload) ?: return
        val importFileId = importFile.importFileId

        try {
            val content = downloadFileContent(importFile)
            val configuration = getImportConfiguration(userId, importFile.importConfigurationId)

            val parseResults = importParserRegistry[importFile.type]
                .parse(configuration.matchers, content)
            val conversionResults =
                importFundConversionService.mapToFundRequest(userId, parseResults.mapNotNull { it.getOrNull() })

            val aggregatedException = (parseResults + conversionResults)
                .mapNotNull { it.exceptionOrNull() as? ImportDataException }
                .reduceOrNull { acc, e -> acc + e }
            if (aggregatedException != null) {
                log.warn { "Import errors for importFileId=$importFileId: $aggregatedException" }
                importFileRepository.updateStatusWithErrors(
                    userId, importFileId, ImportFileStatus.IMPORT_FAILED, aggregatedException.problems.toList()
                )
                return
            }
            val fundTransactions =
                CreateTransactionsTO(conversionResults.mapNotNull { it.getOrNull() }, "import-file-$importFileId")
            createFundTransactionsProducer.send(Event(userId, fundTransactions, importFileId))
        } catch (e: Exception) {
            log.warn(e) { "Error handling import file command >> importFileId=$importFileId" }
            importFileRepository.updateStatusWithErrors(
                userId, importFileId, ImportFileStatus.IMPORT_FAILED,
                listOf(e.message ?: "Unknown error"),
            )
        }
    }

    private suspend fun getImportingFile(userId: Uuid, command: ImportFileCommandTO): ImportFile? {
        val importFileId = command.importFileId
        log.info { "Handling import file command >> importFileId=$importFileId" }

        val importFile = importFileRepository.findById(userId, importFileId)
        if (importFile == null) {
            log.warn { "Import file not found >> importFileId=$importFileId" }
            return null
        }
        if (importFile.status != ImportFileStatus.IMPORTING) {
            log.warn { "Import file not in IMPORTING status >> importFileId=$importFileId, status=${importFile.status}" }
            return null
        }
        return importFile
    }

    private suspend fun downloadFileContent(importFile: ImportFile): String {
        return s3Client.getObject(GetObjectRequest {
            this.bucket = s3Configuration.bucket
            this.key = importFile.s3Key
        }) { response ->
            response.body?.toByteArray()?.decodeToString() ?: ""
        }
    }

    private suspend fun getImportConfiguration(userId: Uuid, importConfigurationId: Uuid): ImportConfiguration {
        return importConfigurationService.getImportConfiguration(userId, importConfigurationId)
            ?: throw ImportConfigurationNotFoundException(importConfigurationId)
    }
}
