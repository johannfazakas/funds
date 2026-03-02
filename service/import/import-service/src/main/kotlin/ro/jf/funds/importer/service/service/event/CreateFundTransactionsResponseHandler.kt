package ro.jf.funds.importer.service.service.event

import mu.KotlinLogging.logger
import ro.jf.funds.importer.service.domain.ImportFileStatus
import ro.jf.funds.importer.service.persistence.ImportFileRepository
import ro.jf.funds.platform.jvm.event.Event
import ro.jf.funds.platform.jvm.event.EventHandler
import ro.jf.funds.platform.jvm.model.GenericResponse

private val log = logger { }

class CreateFundTransactionsResponseHandler(
    private val importFileRepository: ImportFileRepository,
) : EventHandler<GenericResponse> {
    override suspend fun handle(event: Event<GenericResponse>) {
        log.info { "Received event: $event" }
        val importFileId = event.correlationId ?: error("Missing correlationId")
        when (val genericResponse = event.payload) {
            is GenericResponse.Success -> {
                log.info { "Updating import file status >> importFileId=$importFileId, status=IMPORTED" }
                importFileRepository.updateStatus(event.userId, importFileId, ImportFileStatus.IMPORTED)
            }
            is GenericResponse.Error -> {
                val reason = genericResponse.reason
                log.info { "Updating import file status >> importFileId=$importFileId, status=IMPORT_FAILED" }
                importFileRepository.updateStatusWithErrors(
                    event.userId, importFileId, ImportFileStatus.IMPORT_FAILED, listOf(reason)
                )
            }
        }
    }
}
