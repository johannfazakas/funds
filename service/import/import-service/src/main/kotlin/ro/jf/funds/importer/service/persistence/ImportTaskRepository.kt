package ro.jf.funds.importer.service.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
import ro.jf.funds.importer.service.domain.*
import java.util.*

class ImportTaskRepository(
    private val database: Database,
) {
    object ImportTaskTable : UUIDTable("import_task") {
        val userId = uuid("user_id")
        val importFileId = uuid("import_file_id")
            .references(ImportFileRepository.ImportFileTable.id)
            .nullable()
    }

    object ImportTaskPartTable : UUIDTable("import_task_part") {
        val taskId = reference("task_id", ImportTaskTable)
        val name = varchar("name", 255)
        val status = varchar("status", 50)
        val reason = varchar("reason", 255).nullable()
    }

    suspend fun listImportTasks(userId: UUID): List<ImportTask> = blockingTransaction {
        (ImportTaskTable leftJoin ImportTaskPartTable)
            .selectAll()
            .where { ImportTaskTable.userId eq userId }
            .toImportTasks()
    }

    suspend fun findImportTaskById(userId: UUID, taskId: UUID): ImportTask? = blockingTransaction {
        (ImportTaskTable leftJoin ImportTaskPartTable)
            .selectAll()
            .where { (ImportTaskTable.userId eq userId) and (ImportTaskTable.id eq taskId) }
            .toImportTasks()
            .singleOrNull()
    }

    suspend fun startImportTask(command: StartImportTaskCommand): ImportTask = blockingTransaction {
        val task = ImportTaskTable.insert {
            it[ImportTaskTable.userId] = command.userId
            it[ImportTaskTable.importFileId] = command.importFileId
        }
        val taskId = task[ImportTaskTable.id].value
        val taskParts = ImportTaskPartTable
            .batchInsert(command.partNames, shouldReturnGeneratedValues = false) { partName ->
                this[ImportTaskPartTable.taskId] = taskId
                this[ImportTaskPartTable.name] = partName
                this[ImportTaskPartTable.status] = ImportTaskPartStatus.IN_PROGRESS.name
            }
        ImportTask(
            taskId = taskId,
            userId = command.userId,
            parts = taskParts.map { row ->
                ImportTaskPart(
                    taskPartId = row[ImportTaskPartTable.id].value,
                    name = row[ImportTaskPartTable.name],
                    status = ImportTaskPartStatus.valueOf(row[ImportTaskPartTable.status]),
                    reason = row[ImportTaskPartTable.reason]
                )
            }
        )
    }

    suspend fun findImportTaskByPartId(taskPartId: UUID): ImportTask? = blockingTransaction {
        val taskId = ImportTaskPartTable
            .selectAll()
            .where { ImportTaskPartTable.id eq taskPartId }
            .singleOrNull()
            ?.get(ImportTaskPartTable.taskId)
            ?.value
            ?: return@blockingTransaction null
        (ImportTaskTable leftJoin ImportTaskPartTable)
            .selectAll()
            .where { ImportTaskTable.id eq taskId }
            .toImportTasks()
            .singleOrNull()
    }

    suspend fun updateTaskPart(command: UpdateImportTaskPartCommand) = blockingTransaction {
        ImportTaskPartTable.update({ ImportTaskPartTable.id eq command.taskPartId }) {
            it[status] = command.status.name
            it[reason] = command.reason
        }
    }

    suspend fun deleteAll(): Unit = blockingTransaction {
        ImportTaskTable.deleteAll()
    }

    private fun Query.toImportTasks(): List<ImportTask> = this
        .groupBy { it[ImportTaskTable.id].value }
        .map { (_, rows) -> rows.toImportTask() }

    private fun List<ResultRow>.toImportTask() = ImportTask(
        taskId = this.first()[ImportTaskTable.id].value,
        userId = this.first()[ImportTaskTable.userId],
        importFileId = this.first()[ImportTaskTable.importFileId],
        parts = this.map { row ->
            ImportTaskPart(
                taskPartId = row[ImportTaskPartTable.id].value,
                name = row[ImportTaskPartTable.name],
                status = ImportTaskPartStatus.valueOf(row[ImportTaskPartTable.status]),
                reason = row[ImportTaskPartTable.reason]
            )
        }
    )
}
