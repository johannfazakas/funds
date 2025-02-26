package ro.jf.funds.reporting.service.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.model.asLabels
import ro.jf.funds.commons.model.asString
import ro.jf.funds.commons.persistence.blockingTransaction
import ro.jf.funds.reporting.service.domain.ReportView
import java.util.*

class ReportViewRepository(
    private val database: Database,
) {
    object ReportViewTable : UUIDTable("report_view") {
        val userId = uuid("user_id")
        val name = varchar("name", 50)
        val fundId = uuid("fund_id")
        val currency = varchar("currency", 50)
        val labels = varchar("labels", 100)
//        TODO(Johann-11) add configuration in db
//        val configuration = json("configuration", )
    }

    suspend fun save(
        userId: UUID,
        name: String,
        fundId: UUID,
        currency: Currency,
        labels: List<Label>,
    ): ReportView = blockingTransaction {
        ReportViewTable.insert {
            it[ReportViewTable.userId] = userId
            it[ReportViewTable.name] = name
            it[ReportViewTable.fundId] = fundId
            it[ReportViewTable.currency] = currency.value
            it[ReportViewTable.labels] = labels.asString()
        }.let {
            ReportView(
                id = it[ReportViewTable.id].value,
                userId = it[ReportViewTable.userId],
                name = it[ReportViewTable.name],
                fundId = it[ReportViewTable.fundId],
                currency = Currency(it[ReportViewTable.currency]),
                labels = it[ReportViewTable.labels].asLabels(),
            )
        }
    }

    suspend fun findByName(
        userId: UUID,
        name: String,
    ): ReportView? = blockingTransaction {
        ReportViewTable
            // TODO(Johann) Fix deprecated select calls
            .select { (ReportViewTable.userId eq userId) and (ReportViewTable.name eq name) }
            .map { it.toModel() }
            .singleOrNull()
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

    suspend fun delete(userId: UUID, reportViewId: UUID): Unit = blockingTransaction {
        ReportViewTable.deleteWhere {
            (ReportViewTable.userId eq userId) and (ReportViewTable.id eq reportViewId)
        }
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
            currency = Currency(this[ReportViewTable.currency]),
            labels = this[ReportViewTable.labels].asLabels(),
        )
}
