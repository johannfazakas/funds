package ro.jf.funds.reporting.service.persistence

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.date
import ro.jf.funds.commons.model.asLabels
import ro.jf.funds.commons.model.asString
import ro.jf.funds.commons.model.toFinancialUnit
import ro.jf.funds.commons.persistence.blockingTransaction
import ro.jf.funds.reporting.api.model.DateIntervalTO
import ro.jf.funds.reporting.service.domain.CreateReportRecordCommand
import ro.jf.funds.reporting.service.domain.ReportRecord
import java.util.*

// TODO(Johann) can be removed
class ReportRecordRepository(
    private val database: Database,
) {
    object ReportRecordTable : UUIDTable("report_record") {
        val userId = uuid("user_id")
        val reportViewId = uuid("report_view_id")
        val record_id = uuid("record_id")
        val date = date("date")
        val unit = varchar("unit", 50)
        val unitType = varchar("unit_type", 50)
        val amount = decimal("amount", 20, 8)
        val labels = varchar("labels", 100)
    }

    suspend fun save(command: CreateReportRecordCommand): ReportRecord =
        blockingTransaction {
            saveRecord(command)
        }

    suspend fun saveAll(commands: List<CreateReportRecordCommand>): List<ReportRecord> =
        blockingTransaction {
            commands.map { saveRecord(it) }
        }

    suspend fun findByViewUntil(userId: UUID, reportViewId: UUID, until: LocalDate) = blockingTransaction {
        ReportRecordTable
            .selectAll()
            .where {
                (ReportRecordTable.userId eq userId) and
                        (ReportRecordTable.reportViewId eq reportViewId) and
                        (ReportRecordTable.date lessEq until.toJavaLocalDate())
            }
            .map { it.toModel() }
    }

    suspend fun findByViewInInterval(userId: UUID, reportViewId: UUID, interval: DateIntervalTO) = blockingTransaction {
        ReportRecordTable
            .selectAll()
            .where {
                (ReportRecordTable.userId eq userId) and
                        (ReportRecordTable.reportViewId eq reportViewId) and
                        (ReportRecordTable.date greaterEq interval.from.toJavaLocalDate()) and
                        (ReportRecordTable.date lessEq interval.to.toJavaLocalDate())
            }
            .map { it.toModel() }
    }

    private fun ResultRow.toModel(): ReportRecord =
        ReportRecord(
            id = this[ReportRecordTable.id].value,
            userId = this[ReportRecordTable.userId],
            reportViewId = this[ReportRecordTable.reportViewId],
            recordId = this[ReportRecordTable.record_id],
            date = this[ReportRecordTable.date].toKotlinLocalDate(),
            unit = toFinancialUnit(this[ReportRecordTable.unitType], this[ReportRecordTable.unit]),
            amount = this[ReportRecordTable.amount],
            labels = this[ReportRecordTable.labels].asLabels()
        )

    private fun saveRecord(command: CreateReportRecordCommand): ReportRecord {
        val reportRecord = ReportRecordTable.insert {
            it[userId] = command.userId
            it[reportViewId] = command.reportViewId
            it[record_id] = command.recordId
            it[date] = command.date.toJavaLocalDate()
            it[unit] = command.unit.value
            it[unitType] = command.unit.type.value
            it[amount] = command.amount
            it[labels] = command.labels.asString()
        }
        return reportRecord.let {
            ReportRecord(
                id = it[ReportRecordTable.id].value,
                userId = it[ReportRecordTable.userId],
                recordId = it[ReportRecordTable.record_id],
                reportViewId = it[ReportRecordTable.reportViewId],
                date = it[ReportRecordTable.date].toKotlinLocalDate(),
                unit = toFinancialUnit(it[ReportRecordTable.unitType], it[ReportRecordTable.unit]),
                amount = it[ReportRecordTable.amount],
                labels = it[ReportRecordTable.labels].asLabels()
            )
        }
    }
}
