package ro.jf.funds.reporting.service.persistence

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.date
import ro.jf.funds.commons.service.persistence.blockingTransaction
import ro.jf.funds.reporting.api.model.DateInterval
import ro.jf.funds.reporting.service.domain.ReportRecord
import java.math.BigDecimal
import java.util.*

class ReportRecordRepository(
    private val database: Database,
) {
    object ReportRecordTable : UUIDTable("report_record") {
        val userId = uuid("user_id")
        val reportViewId = uuid("report_view_id")
        val date = date("date")
        val amount = decimal("amount", 10, 2)
    }

    suspend fun create(userId: UUID, reportViewId: UUID, date: LocalDate, amount: BigDecimal): ReportRecord =
        blockingTransaction {
            val reportRecord = ReportRecordTable.insert {
                it[ReportRecordTable.userId] = userId
                it[ReportRecordTable.reportViewId] = reportViewId
                it[ReportRecordTable.date] = date.toJavaLocalDate()
                it[ReportRecordTable.amount] = amount
            }
            reportRecord.let {
                ReportRecord(
                    id = it[ReportRecordTable.id].value,
                    userId = it[ReportRecordTable.userId],
                    reportViewId = it[ReportRecordTable.reportViewId],
                    date = it[ReportRecordTable.date].toKotlinLocalDate(),
                    amount = it[ReportRecordTable.amount],
                )
            }
        }

    suspend fun findByInterval(userId: UUID, interval: DateInterval) = blockingTransaction {
        ReportRecordTable
            .select {
                (ReportRecordTable.userId eq userId) and
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
            date = this[ReportRecordTable.date].toKotlinLocalDate(),
            amount = this[ReportRecordTable.amount],
        )
}
