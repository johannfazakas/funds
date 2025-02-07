package ro.jf.funds.reporting.service.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import ro.jf.funds.commons.persistence.blockingTransaction
import ro.jf.funds.reporting.service.domain.ReportViewTask
import java.util.*

class ReportViewTaskRepository(
    private val database: Database,
) {
    object ReportViewTaskTable : UUIDTable("report_view_task") {
        val userId = uuid("user_id")
        val status = varchar("status", 50)
        val reportViewId = uuid("report_view_id").nullable()
        val reason = varchar("reason", 255).nullable()
    }

    suspend fun create(
        userId: UUID,
    ): ReportViewTask = blockingTransaction {
        ReportViewTaskTable.insert {
            it[ReportViewTaskTable.userId] = userId
            it[ReportViewTaskTable.status] = ReportViewTask.Status.IN_PROGRESS.name
            it[ReportViewTaskTable.reportViewId] = null
            it[ReportViewTaskTable.reason] = null
        }.let {
            ReportViewTask.InProgress(
                userId = it[ReportViewTaskTable.userId],
                taskId = it[ReportViewTaskTable.id].value,
            )
        }
    }

    suspend fun complete(userId: UUID, reportViewTaskId: UUID, reportViewId: UUID): ReportViewTask =
        blockingTransaction {
            ReportViewTaskTable.update({ (ReportViewTaskTable.userId eq userId) and (ReportViewTaskTable.id eq reportViewTaskId) }) {
                it[ReportViewTaskTable.status] = ReportViewTask.Status.COMPLETED.name
                it[ReportViewTaskTable.reportViewId] = reportViewId
            }
            ReportViewTask.Completed(
                userId = userId,
                taskId = reportViewTaskId,
                reportViewId = reportViewId,
            )
        }

    suspend fun fail(userId: UUID, reportViewTaskId: UUID, reason: String): ReportViewTask = blockingTransaction {
        ReportViewTaskTable.update({ (ReportViewTaskTable.userId eq userId) and (ReportViewTaskTable.id eq reportViewTaskId) }) {
            it[ReportViewTaskTable.status] = ReportViewTask.Status.FAILED.name
            it[ReportViewTaskTable.reason] = reason
        }
        ReportViewTask.Failed(
            userId = userId,
            taskId = reportViewTaskId,
            reason = reason,
        )
    }

    suspend fun findById(userId: UUID, reportViewTaskId: UUID): ReportViewTask? = blockingTransaction {
        ReportViewTaskTable
            .select { (ReportViewTaskTable.userId eq userId) and (ReportViewTaskTable.id eq reportViewTaskId) }
            .map { it.toModel() }
            .singleOrNull()
    }

    suspend fun deleteAll(): Unit = blockingTransaction { ReportViewTaskTable.deleteAll() }

    private fun ResultRow.toModel() =
        when (ReportViewTask.Status.fromString(this[ReportViewTaskTable.status])) {
            ReportViewTask.Status.IN_PROGRESS -> ReportViewTask.InProgress(
                userId = this[ReportViewTaskTable.userId],
                taskId = this[ReportViewTaskTable.id].value,
            )

            ReportViewTask.Status.COMPLETED -> ReportViewTask.Completed(
                userId = this[ReportViewTaskTable.userId],
                taskId = this[ReportViewTaskTable.id].value,
                reportViewId = this[ReportViewTaskTable.reportViewId]!!,
            )

            ReportViewTask.Status.FAILED -> ReportViewTask.Failed(
                userId = this[ReportViewTaskTable.userId],
                taskId = this[ReportViewTaskTable.id].value,
                reason = this[ReportViewTaskTable.reason]!!,
            )
        }
}
