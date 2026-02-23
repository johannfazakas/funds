package ro.jf.funds.importer.service.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.importer.service.domain.CreateImportFileCommand
import ro.jf.funds.importer.service.domain.ImportFile
import ro.jf.funds.importer.service.domain.ImportFileStatus
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
import java.util.*

class ImportFileRepository(
    private val database: Database,
) {
    object ImportFileTable : UUIDTable("import_file") {
        val userId = uuid("user_id")
        val fileName = varchar("file_name", 255)
        val type = varchar("type", 50)
        val s3Key = varchar("s3_key", 512)
        val status = varchar("status", 50)
    }

    suspend fun create(command: CreateImportFileCommand): ImportFile = blockingTransaction {
        val row = ImportFileTable.insert {
            it[userId] = command.userId
            it[fileName] = command.fileName
            it[type] = command.type.name
            it[s3Key] = command.s3Key
            it[status] = ImportFileStatus.PENDING.name
        }
        ImportFile(
            importFileId = row[ImportFileTable.id].value,
            userId = command.userId,
            fileName = command.fileName,
            type = command.type,
            s3Key = command.s3Key,
            status = ImportFileStatus.PENDING,
        )
    }

    suspend fun confirmUpload(userId: UUID, importFileId: UUID): ImportFile? = blockingTransaction {
        val updated = ImportFileTable.update({
            (ImportFileTable.id eq importFileId) and (ImportFileTable.userId eq userId)
        }) {
            it[status] = ImportFileStatus.UPLOADED.name
        }
        if (updated == 0) return@blockingTransaction null
        findById(userId, importFileId)
    }

    suspend fun findById(userId: UUID, importFileId: UUID): ImportFile? = blockingTransaction {
        ImportFileTable
            .selectAll()
            .where { (ImportFileTable.userId eq userId) and (ImportFileTable.id eq importFileId) }
            .singleOrNull()
            ?.toImportFile()
    }

    suspend fun listByUserId(userId: UUID): List<ImportFile> = blockingTransaction {
        ImportFileTable
            .selectAll()
            .where { ImportFileTable.userId eq userId }
            .map { it.toImportFile() }
    }

    suspend fun deleteAll(): Unit = blockingTransaction {
        ImportFileTable.deleteAll()
    }

    private fun ResultRow.toImportFile() = ImportFile(
        importFileId = this[ImportFileTable.id].value,
        userId = this[ImportFileTable.userId],
        fileName = this[ImportFileTable.fileName],
        type = ImportFileTypeTO.valueOf(this[ImportFileTable.type]),
        s3Key = this[ImportFileTable.s3Key],
        status = ImportFileStatus.valueOf(this[ImportFileTable.status]),
    )
}
