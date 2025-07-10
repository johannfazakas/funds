package ro.jf.funds.importer.service.service.event

import mu.KotlinLogging.logger
import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.event.EventHandler
import ro.jf.funds.commons.model.GenericResponse
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.importer.service.service.ImportService

private val log = logger { }

class CreateFundTransactionsResponseHandler(
    private val importService: ImportService,
) : EventHandler<GenericResponse> {
    override suspend fun handle(event: Event<GenericResponse>): Unit = withSuspendingSpan {
        log.info { "Received event: $event" }
        val importTaskId = event.correlationId ?: error("Missing correlationId")
        when (val genericResponse = event.payload) {
            is GenericResponse.Success -> importService.completeImport(event.userId, importTaskId)
            is GenericResponse.Error -> importService.failImport(
                event.userId,
                importTaskId,
                genericResponse.reason.detail
            )
        }
    }
}
