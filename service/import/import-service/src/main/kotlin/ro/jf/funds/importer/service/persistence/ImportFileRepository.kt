package ro.jf.funds.importer.service.persistence

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.json
import ro.jf.funds.importer.api.model.ImportFileSortField
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.importer.service.domain.CreateImportFileCommand
import ro.jf.funds.importer.service.domain.ImportFile
import ro.jf.funds.importer.service.domain.ImportFileFilter
import ro.jf.funds.importer.service.domain.ImportFileStatus
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.platform.jvm.error.ErrorTO
import ro.jf.funds.platform.jvm.persistence.PagedResult
import ro.jf.funds.platform.jvm.persistence.applyFilterIfPresent
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
import ro.jf.funds.platform.jvm.persistence.toExposedSortOrder
import java.time.LocalDateTime
import java.util.*

class ImportFileRepository(
    private val database: Database,
) {
    object ImportFileTable : UUIDTable("import_file") {
        val userId = uuid("user_id")
        val fileName = varchar("file_name", 255)
        val type = varchar("type", 50)
        val status = varchar("status", 50)
        val importConfigurationId = uuid("import_configuration_id")
            .references(ImportConfigurationRepository.ImportConfigurationTable.id)
        val createdAt = datetime("created_at")
        val errors = json<List<ErrorTO>>("errors", Json.Default).nullable()
    }

    suspend fun create(command: CreateImportFileCommand): ImportFile = blockingTransaction {
        val now = LocalDateTime.now()
        val row = ImportFileTable.insert {
            it[userId] = command.userId
            it[fileName] = command.fileName
            it[type] = command.type.name
            it[status] = ImportFileStatus.PENDING.name
            it[importConfigurationId] = command.importConfigurationId
            it[createdAt] = now
        }
        ImportFile(
            importFileId = row[ImportFileTable.id].value,
            userId = command.userId,
            fileName = command.fileName,
            type = command.type,
            status = ImportFileStatus.PENDING,
            importConfigurationId = command.importConfigurationId,
            createdAt = now,
        )
    }

    suspend fun updateStatus(userId: UUID, importFileId: UUID, status: ImportFileStatus): Boolean = blockingTransaction {
        val updated = ImportFileTable.update({
            (ImportFileTable.id eq importFileId) and (ImportFileTable.userId eq userId)
        }) {
            it[ImportFileTable.status] = status.name
        }
        updated > 0
    }

    suspend fun updateStatusWithErrors(
        userId: UUID,
        importFileId: UUID,
        status: ImportFileStatus,
        errors: List<ErrorTO>,
    ): Boolean = blockingTransaction {
        val updated = ImportFileTable.update({
            (ImportFileTable.id eq importFileId) and (ImportFileTable.userId eq userId)
        }) {
            it[ImportFileTable.status] = status.name
            it[ImportFileTable.errors] = errors
        }
        updated > 0
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

    suspend fun list(
        userId: UUID,
        filter: ImportFileFilter? = null,
        pageRequest: PageRequest? = null,
        sortRequest: SortRequest<ImportFileSortField>? = null,
    ): PagedResult<ImportFile> = blockingTransaction {
        val total = ImportFileTable
            .selectAll()
            .where { ImportFileTable.userId eq userId }
            .applyFiltering(filter)
            .count()

        val items = ImportFileTable
            .selectAll()
            .where { ImportFileTable.userId eq userId }
            .applyFiltering(filter)
            .applySorting(sortRequest)
            .applyPagination(pageRequest)
            .map { it.toImportFile() }

        PagedResult(items, total)
    }

    suspend fun delete(userId: UUID, importFileId: UUID): Boolean = blockingTransaction {
        val deleted = ImportFileTable.deleteWhere {
            (ImportFileTable.id eq importFileId) and (ImportFileTable.userId eq userId)
        }
        deleted > 0
    }

    suspend fun deleteAll(): Unit = blockingTransaction {
        ImportFileTable.deleteAll()
    }

    private fun ResultRow.toImportFile() = ImportFile(
        importFileId = this[ImportFileTable.id].value,
        userId = this[ImportFileTable.userId],
        fileName = this[ImportFileTable.fileName],
        type = ImportFileTypeTO.valueOf(this[ImportFileTable.type]),
        status = ImportFileStatus.valueOf(this[ImportFileTable.status]),
        importConfigurationId = this[ImportFileTable.importConfigurationId],
        createdAt = this[ImportFileTable.createdAt],
        errors = this[ImportFileTable.errors] ?: emptyList(),
    )

    private fun Query.applyFiltering(filter: ImportFileFilter?): Query {
        if (filter == null) return this
        return this
            .applyFilterIfPresent(filter.type) { ImportFileTable.type eq it.name }
            .applyFilterIfPresent(filter.status) { ImportFileTable.status eq it.name }
            .applyFilterIfPresent(filter.importConfigurationId) { ImportFileTable.importConfigurationId eq it }
    }

    private fun Query.applySorting(sortRequest: SortRequest<ImportFileSortField>?): Query =
        sortRequest?.let {
            val sortColumn = when (it.field) {
                ImportFileSortField.FILE_NAME -> ImportFileTable.fileName
                ImportFileSortField.CREATED_AT -> ImportFileTable.createdAt
            }
            orderBy(sortColumn to it.order.toExposedSortOrder())
        } ?: this

    private fun Query.applyPagination(pageRequest: PageRequest?): Query =
        pageRequest?.let { limit(it.limit).offset(it.offset.toLong()) } ?: this
}
