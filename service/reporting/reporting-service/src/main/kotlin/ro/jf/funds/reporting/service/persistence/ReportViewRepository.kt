package ro.jf.funds.reporting.service.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import ro.jf.funds.commons.service.persistence.blockingTransaction
import ro.jf.funds.reporting.api.model.ReportViewType
import ro.jf.funds.reporting.service.domain.ReportView
import java.util.*

class ReportViewRepository(
    private val database: Database,
) {
    object ReportViewTable : UUIDTable("report_view") {
        val userId = uuid("user_id")
        val name = varchar("name", 50)
        val fundId = uuid("fund_id")
        val type = varchar("type", 50)
    }

    suspend fun create(
        userId: UUID,
        name: String,
        fundId: UUID,
        type: ReportViewType,
    ): ReportView = blockingTransaction {
        ReportViewTable.insert {
            it[ReportViewTable.userId] = userId
            it[ReportViewTable.name] = name
            it[ReportViewTable.fundId] = fundId
            it[ReportViewTable.type] = type.name
        }.let {
            ReportView(
                id = it[ReportViewTable.id].value,
                userId = it[ReportViewTable.userId],
                name = it[ReportViewTable.name],
                fundId = it[ReportViewTable.fundId],
                type = it[ReportViewTable.type].let(ReportViewType::fromString),
            )
        }
    }

    suspend fun findById(
        userId: UUID,
        reportViewId: UUID,
    ): ReportView? = blockingTransaction {
        ReportViewTable
            .select { (ReportViewTable.userId eq userId) and (ReportViewTable.id eq reportViewId) }
            .map { it.toModel() }
            .singleOrNull()
    }

    suspend fun deleteAll(): Unit = blockingTransaction { ReportViewTable.deleteAll() }

    suspend fun findAll(userId: UUID): List<ReportView> = blockingTransaction {
        ReportViewTable
            .select { ReportViewTable.userId eq userId }
            .map { it.toModel() }
    }

    private fun ResultRow.toModel(): ReportView =
        ReportView(
            id = this[ReportViewTable.id].value,
            userId = this[ReportViewTable.userId],
            name = this[ReportViewTable.name],
            fundId = this[ReportViewTable.fundId],
            type = this[ReportViewTable.type].let(ReportViewType::fromString),
        )
}
