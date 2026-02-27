package ro.jf.funds.importer.service.service.event

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.content.toByteArray
import mu.KotlinLogging.logger
import ro.jf.funds.importer.api.model.ImportFileCommandTO
import ro.jf.funds.importer.service.config.S3Configuration
import ro.jf.funds.importer.service.domain.ImportFileStatus
import ro.jf.funds.importer.service.domain.RawImportFile
import ro.jf.funds.importer.service.persistence.ImportFileRepository
import ro.jf.funds.importer.service.service.ImportConfigurationService
import ro.jf.funds.importer.service.service.ImportService
import ro.jf.funds.platform.jvm.error.ErrorTO
import ro.jf.funds.platform.jvm.event.Event
import ro.jf.funds.platform.jvm.event.EventHandler
import java.util.*

private val log = logger { }

class ImportFileCommandHandler(
    private val importFileRepository: ImportFileRepository,
    private val importConfigurationService: ImportConfigurationService,
    private val importService: ImportService,
    private val s3Client: S3Client,
    private val s3Configuration: S3Configuration,
) : EventHandler<ImportFileCommandTO> {
    override suspend fun handle(event: Event<ImportFileCommandTO>) {
        val userId = event.userId
        val importFileId = UUID.fromString(event.payload.importFileId.toString())
        log.info { "Handling import file command >> importFileId=$importFileId, userId=$userId" }

        val importFile = importFileRepository.findById(userId, importFileId)
        if (importFile == null) {
            log.warn { "Import file not found >> importFileId=$importFileId" }
            return
        }
        if (importFile.status != ImportFileStatus.IMPORTING) {
            log.warn { "Import file not in IMPORTING status >> importFileId=$importFileId, status=${importFile.status}" }
            return
        }

        try {
            val content = s3Client.getObject(GetObjectRequest {
                this.bucket = s3Configuration.bucket
                this.key = importFile.s3Key
            }) { response ->
                response.body?.toByteArray()?.decodeToString() ?: ""
            }

            val configuration =
                importConfigurationService.getImportConfiguration(userId, importFile.importConfigurationId)
            if (configuration == null) {
                importFileRepository.updateStatusWithErrors(
                    userId, importFileId, ImportFileStatus.IMPORT_FAILED,
                    listOf(
                        ErrorTO(
                            "Import configuration not found",
                            "Import configuration ${importFile.importConfigurationId} not found"
                        )
                    ),
                )
                return
            }

            importService.startImport(
                userId,
                importFile.type,
                configuration.matchers,
                listOf(RawImportFile(importFile.fileName, content)),
                importFileId,
            )
        } catch (e: Exception) {
            log.error(e) { "Error handling import file command >> importFileId=$importFileId" }
            importFileRepository.updateStatusWithErrors(
                userId, importFileId, ImportFileStatus.IMPORT_FAILED,
                listOf(ErrorTO("Import error", e.message)),
            )
        }
    }
}
