package ro.jf.funds.importer.service.web.mapper

import ro.jf.funds.importer.api.model.ImportTaskTO
import ro.jf.funds.importer.service.domain.ImportTask
import ro.jf.funds.importer.service.domain.ImportTaskPartStatus

fun ImportTask.toTO(): ImportTaskTO {
    return ImportTaskTO(
        taskId = taskId,
        status = when {
            parts.any { it.status == ImportTaskPartStatus.FAILED } -> ImportTaskTO.Status.FAILED
            parts.any { it.status == ImportTaskPartStatus.IN_PROGRESS } -> ImportTaskTO.Status.IN_PROGRESS
            else -> ImportTaskTO.Status.COMPLETED
        },
        reason = parts.firstOrNull { it.reason != null }?.reason,
    )
}