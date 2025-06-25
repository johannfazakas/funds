package ro.jf.funds.reporting.service.persistence

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.json.json
import ro.jf.funds.commons.persistence.blockingTransaction
import ro.jf.funds.reporting.service.domain.CreateReportViewCommand
import ro.jf.funds.reporting.service.domain.ReportDataConfiguration
import ro.jf.funds.reporting.service.domain.ReportView
import java.util.*

private val jsonBConfig = Json { prettyPrint = true }

class ReportViewRepository(
    private val database: Database,
) {
    object ReportViewTable : UUIDTable("report_view") {
        val userId = uuid("user_id")
        val name = varchar("name", 50)
        val fundId = uuid("fund_id")
        val dataConfiguration = json<ReportDataConfiguration>("data_configuration", jsonBConfig)
    }

    suspend fun save(
        command: CreateReportViewCommand,
    ): ReportView = blockingTransaction {
        ReportViewTable.insert {
            it[ReportViewTable.userId] = command.userId
            it[ReportViewTable.name] = command.name
            it[ReportViewTable.fundId] = command.fundId
            it[ReportViewTable.dataConfiguration] = command.dataConfiguration
        }.let {
            ReportView(
                id = it[ReportViewTable.id].value,
                userId = it[ReportViewTable.userId],
                name = it[ReportViewTable.name],
                fundId = it[ReportViewTable.fundId],
                dataConfiguration = it[ReportViewTable.dataConfiguration],
            )
        }
    }

    suspend fun findByName(
        userId: UUID,
        name: String,
    ): ReportView? = blockingTransaction {
        ReportViewTable
            .selectAll()
            .where { (ReportViewTable.userId eq userId) and (ReportViewTable.name eq name) }
            .map { it.toModel() }
            .singleOrNull()
    }

    suspend fun findById(
        userId: UUID,
        reportViewId: UUID,
    ): ReportView? = blockingTransaction {
        ReportViewTable
            .selectAll()
            .where { (ReportViewTable.userId eq userId) and (ReportViewTable.id eq reportViewId) }
            .map { it.toModel() }
            .singleOrNull()
    }

    suspend fun delete(userId: UUID, reportViewId: UUID): Unit = blockingTransaction {
        ReportViewTable.deleteWhere {
            (ReportViewTable.userId eq userId) and (ReportViewTable.id eq reportViewId)
        }
    }

    suspend fun deleteAll(): Unit = blockingTransaction { ReportViewTable.deleteAll() }

    suspend fun findAll(userId: UUID): List<ReportView> = blockingTransaction {
        ReportViewTable
            .selectAll()
            .where { ReportViewTable.userId eq userId }
            .map { it.toModel() }
    }

    private fun ResultRow.toModel(): ReportView =
        ReportView(
            id = this[ReportViewTable.id].value,
            userId = this[ReportViewTable.userId],
            name = this[ReportViewTable.name],
            fundId = this[ReportViewTable.fundId],
            dataConfiguration = this[ReportViewTable.dataConfiguration],
        )
}
