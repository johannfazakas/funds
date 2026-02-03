package ro.jf.funds.reporting.service.domain

import com.benasher44.uuid.Uuid
import java.math.BigDecimal as JavaBigDecimal
import kotlinx.datetime.LocalDate
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Label

sealed class ReportTransaction {
    abstract val date: LocalDate
    abstract val records: List<ReportRecord>

    data class SingleRecord(
        override val date: LocalDate,
        val record: ReportRecord,
    ) : ReportTransaction() {
        override val records: List<ReportRecord> = listOf(record)
    }

    data class Transfer(
        override val date: LocalDate,
        val sourceRecord: ReportRecord?,
        val destinationRecord: ReportRecord?,
    ) : ReportTransaction() {
        override val records: List<ReportRecord> = listOfNotNull(sourceRecord, destinationRecord)
    }

    data class Exchange(
        override val date: LocalDate,
        val sourceRecord: ReportRecord?,
        val destinationRecord: ReportRecord?,
        val feeRecord: ReportRecord?,
    ) : ReportTransaction() {
        override val records: List<ReportRecord> = listOfNotNull(sourceRecord, destinationRecord, feeRecord)
    }

    data class OpenPosition(
        override val date: LocalDate,
        val currencyRecord: ReportRecord,
        val instrumentRecord: ReportRecord,
    ) : ReportTransaction() {
        override val records: List<ReportRecord> = listOf(currencyRecord, instrumentRecord)
    }

    data class ClosePosition(
        override val date: LocalDate,
        val currencyRecord: ReportRecord,
        val instrumentRecord: ReportRecord,
    ) : ReportTransaction() {
        override val records: List<ReportRecord> = listOf(currencyRecord, instrumentRecord)
    }
}

data class ReportRecord(
    val date: LocalDate,
    val fundId: Uuid,
    val unit: FinancialUnit,
    val amount: JavaBigDecimal,
    val labels: List<Label>,
)
