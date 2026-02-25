package ro.jf.funds.importer.service.persistence

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.json
import ro.jf.funds.importer.api.model.ImportConfigurationSortField
import ro.jf.funds.importer.service.domain.CreateImportConfigurationCommand
import ro.jf.funds.importer.service.domain.ImportConfiguration
import ro.jf.funds.importer.service.domain.ImportConfigurationMatchersTO
import ro.jf.funds.importer.service.domain.UpdateImportConfigurationCommand
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.platform.jvm.persistence.PagedResult
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
import ro.jf.funds.platform.jvm.persistence.toExposedSortOrder
import java.time.LocalDateTime
import java.util.*

class ImportConfigurationRepository(
    private val database: Database,
) {
    object ImportConfigurationTable : UUIDTable("import_configuration") {
        val userId = uuid("user_id")
        val name = varchar("name", 255)
        val matchers = json<ImportConfigurationMatchersTO>("matchers", Json.Default)
        val createdAt = datetime("created_at")
    }

    suspend fun create(command: CreateImportConfigurationCommand): ImportConfiguration = blockingTransaction {
        val now = LocalDateTime.now()
        val row = ImportConfigurationTable.insert {
            it[userId] = command.userId
            it[name] = command.name
            it[matchers] = command.matchers
            it[createdAt] = now
        }
        ImportConfiguration(
            importConfigurationId = row[ImportConfigurationTable.id].value,
            userId = command.userId,
            name = command.name,
            matchers = command.matchers,
            createdAt = now,
        )
    }

    suspend fun findById(userId: UUID, importConfigurationId: UUID): ImportConfiguration? = blockingTransaction {
        ImportConfigurationTable
            .selectAll()
            .where { (ImportConfigurationTable.userId eq userId) and (ImportConfigurationTable.id eq importConfigurationId) }
            .singleOrNull()
            ?.toImportConfiguration()
    }

    suspend fun list(
        userId: UUID,
        pageRequest: PageRequest? = null,
        sortRequest: SortRequest<ImportConfigurationSortField>? = null,
    ): PagedResult<ImportConfiguration> = blockingTransaction {
        val total = ImportConfigurationTable
            .selectAll()
            .where { ImportConfigurationTable.userId eq userId }
            .count()

        val items = ImportConfigurationTable
            .selectAll()
            .where { ImportConfigurationTable.userId eq userId }
            .applySorting(sortRequest)
            .applyPagination(pageRequest)
            .map { it.toImportConfiguration() }

        PagedResult(items, total)
    }

    suspend fun update(
        userId: UUID,
        importConfigurationId: UUID,
        command: UpdateImportConfigurationCommand,
    ): ImportConfiguration? = blockingTransaction {
        val existing = ImportConfigurationTable
            .selectAll()
            .where { (ImportConfigurationTable.userId eq userId) and (ImportConfigurationTable.id eq importConfigurationId) }
            .singleOrNull()
            ?.toImportConfiguration()
            ?: return@blockingTransaction null

        ImportConfigurationTable.update({
            (ImportConfigurationTable.userId eq userId) and (ImportConfigurationTable.id eq importConfigurationId)
        }) {
            if (command.name != null) it[name] = command.name
            if (command.matchers != null) it[matchers] = command.matchers
        }

        findById(userId, importConfigurationId)
    }

    suspend fun delete(userId: UUID, importConfigurationId: UUID): Boolean = blockingTransaction {
        val deleted = ImportConfigurationTable.deleteWhere {
            (ImportConfigurationTable.id eq importConfigurationId) and (ImportConfigurationTable.userId eq userId)
        }
        deleted > 0
    }

    suspend fun deleteAll(): Unit = blockingTransaction {
        ImportConfigurationTable.deleteAll()
    }

    private fun ResultRow.toImportConfiguration() = ImportConfiguration(
        importConfigurationId = this[ImportConfigurationTable.id].value,
        userId = this[ImportConfigurationTable.userId],
        name = this[ImportConfigurationTable.name],
        matchers = this[ImportConfigurationTable.matchers],
        createdAt = this[ImportConfigurationTable.createdAt],
    )

    private fun Query.applySorting(sortRequest: SortRequest<ImportConfigurationSortField>?): Query =
        sortRequest?.let {
            val sortColumn = when (it.field) {
                ImportConfigurationSortField.NAME -> ImportConfigurationTable.name
                ImportConfigurationSortField.CREATED_AT -> ImportConfigurationTable.createdAt
            }
            orderBy(sortColumn to it.order.toExposedSortOrder())
        } ?: this

    private fun Query.applyPagination(pageRequest: PageRequest?): Query =
        pageRequest?.let { limit(it.limit).offset(it.offset.toLong()) } ?: this
}
