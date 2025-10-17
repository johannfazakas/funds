package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import java.math.BigDecimal
import java.util.UUID

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
        val sourceRecord: ReportRecord,
        val destinationRecord: ReportRecord,
    ) : ReportTransaction() {
        override val records: List<ReportRecord> = listOf(sourceRecord, destinationRecord)
    }

    data class Exchange(
        override val date: LocalDate,
        val sourceRecord: ReportRecord,
        val destinationRecord: ReportRecord,
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
    val fundId: UUID,
    val unit: FinancialUnit,
    val amount: BigDecimal,
    val labels: List<Label>,
)
