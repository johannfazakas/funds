package ro.jf.funds.reporting.service.service

import mu.KotlinLogging.logger
import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.event.Producer
import ro.jf.funds.reporting.service.domain.CreateReportViewCommand
import ro.jf.funds.reporting.service.domain.ReportViewTask
import ro.jf.funds.reporting.service.persistence.ReportViewTaskRepository
import java.util.*
import kotlin.time.measureTime

private val log = logger { }

class ReportViewTaskService(
    private val reportViewService: ReportViewService,
    private val reportViewTaskRepository: ReportViewTaskRepository,
    private val createReportViewProducer: Producer<CreateReportViewCommand>,
) {
    suspend fun triggerReportViewTask(command: CreateReportViewCommand): ReportViewTask {
        val reportViewTask = reportViewTaskRepository.create(command.userId)
        createReportViewProducer.send(Event(command.userId, command, correlationId = reportViewTask.taskId))
        return reportViewTask
    }

    suspend fun getReportViewTask(userId: UUID, taskId: UUID): ReportViewTask? {
        return reportViewTaskRepository.findById(userId, taskId)
    }

    suspend fun handleReportViewTask(userId: UUID, taskId: UUID, command: CreateReportViewCommand) {
        log.info { "Handle report view task $taskId for user $userId." }
        try {
            measureTime {
                reportViewTaskRepository.findById(userId, taskId) ?: error("Report view task not found")
                val createReportView = reportViewService.createReportView(userId, command)
                reportViewTaskRepository.complete(userId, taskId, createReportView.id)
            }.let { duration ->
                log.info { "Report view task $taskId completed in $duration" }
            }
        } catch (e: Exception) {
            reportViewService.deleteReportView(userId, taskId)
            reportViewTaskRepository.fail(userId, taskId, e.message ?: "Unknown error")
            log.warn(e) { "Report view task $taskId failed." }
        }
    }
}
