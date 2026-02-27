package ro.jf.funds.importer.service.service.event

import mu.KotlinLogging.logger
import ro.jf.funds.importer.service.domain.ImportFileStatus
import ro.jf.funds.importer.service.persistence.ImportFileRepository
import ro.jf.funds.importer.service.persistence.ImportTaskRepository
import ro.jf.funds.importer.service.service.ImportService
import ro.jf.funds.platform.jvm.error.ErrorTO
import ro.jf.funds.platform.jvm.event.Event
import ro.jf.funds.platform.jvm.event.EventHandler
import ro.jf.funds.platform.jvm.model.GenericResponse

private val log = logger { }

class CreateFundTransactionsResponseHandler(
    private val importService: ImportService,
    private val importTaskRepository: ImportTaskRepository,
    private val importFileRepository: ImportFileRepository,
) : EventHandler<GenericResponse> {
    override suspend fun handle(event: Event<GenericResponse>) {
        log.info { "Received event: $event" }
        val taskPartId = event.correlationId ?: error("Missing correlationId")
        when (val genericResponse = event.payload) {
            is GenericResponse.Success -> {
                importService.completeImport(event.userId, taskPartId)
                updateImportFileStatus(taskPartId, ImportFileStatus.IMPORTED)
            }
            is GenericResponse.Error -> {
                val reason = genericResponse.reason
                importService.failImport(event.userId, taskPartId, reason.detail)
                updateImportFileStatus(taskPartId, ImportFileStatus.IMPORT_FAILED, listOf(reason))
            }
        }
    }

    private suspend fun updateImportFileStatus(
        taskPartId: java.util.UUID,
        status: ImportFileStatus,
        errors: List<ErrorTO> = emptyList(),
    ) {
        val importTask = importTaskRepository.findImportTaskByPartId(taskPartId)
        if (importTask?.importFileId == null) return
        log.info { "Updating import file status >> importFileId=${importTask.importFileId}, status=$status" }
        if (errors.isEmpty()) {
            importFileRepository.updateStatus(importTask.userId, importTask.importFileId, status)
        } else {
            importFileRepository.updateStatusWithErrors(importTask.userId, importTask.importFileId, status, errors)
        }
    }
}
