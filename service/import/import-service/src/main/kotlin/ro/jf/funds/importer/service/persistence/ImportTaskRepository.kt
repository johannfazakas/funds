package ro.jf.funds.importer.service.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import ro.jf.funds.commons.service.persistence.blockingTransaction
import ro.jf.funds.importer.api.model.ImportTaskTO
import java.util.*

class ImportTaskRepository(
    private val database: Database
) {
    object ImportTaskTable : UUIDTable("import_task") {
        val userId = uuid("user_id")
        val status = varchar("status", 50)
        val reason = varchar("reason", 255).nullable()
    }

    suspend fun list(userId: UUID): List<ImportTaskTO> = blockingTransaction {
        ImportTaskTable
            .select { ImportTaskTable.userId eq userId }
            .toImportTasks()
    }

    suspend fun findById(userId: UUID, taskId: UUID): ImportTaskTO? = blockingTransaction {
        ImportTaskTable
            .select { (ImportTaskTable.userId eq userId) and (ImportTaskTable.id eq taskId) }
            .toImportTasks()
            .singleOrNull()
    }

    suspend fun save(userId: UUID, status: ImportTaskTO.Status): ImportTaskTO = blockingTransaction {
        val task = ImportTaskTable.insert {
            it[ImportTaskTable.userId] = userId
            it[ImportTaskTable.status] = status.name
            it[ImportTaskTable.reason] = null
        }
        task.let {
            ImportTaskTO(
                taskId = it[ImportTaskTable.id].value,
                status = ImportTaskTO.Status.valueOf(it[ImportTaskTable.status]),
                reason = it[ImportTaskTable.reason]
            )
        }
    }

    suspend fun update(userId: UUID, importTask: ImportTaskTO): ImportTaskTO = blockingTransaction {
        ImportTaskTable.update({ (ImportTaskTable.userId eq userId) and (ImportTaskTable.id eq importTask.taskId) }) {
            it[status] = importTask.status.name
            it[reason] = importTask.reason
        }
        importTask
    }

    private fun Query.toImportTasks(): List<ImportTaskTO> = this
        .groupBy { it[ImportTaskTable.id].value }
        .map { (_, rows) -> rows.toImportTask() }

    private fun List<ResultRow>.toImportTask() = ImportTaskTO(
        taskId = this.first()[ImportTaskTable.id].value,
        status = ImportTaskTO.Status.valueOf(this.first()[ImportTaskTable.status]),
        reason = this.firstOrNull()?.get(ImportTaskTable.reason)
    )
}
