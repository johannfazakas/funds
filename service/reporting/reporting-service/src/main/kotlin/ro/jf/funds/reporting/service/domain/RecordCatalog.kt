package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.api.model.getTimeBucket

class RecordCatalog(
    private val reportRecords: List<ReportRecord>,
    private val granularInterval: GranularDateInterval,
) {
    val previousRecords: Map<FinancialUnit, List<ReportRecord>>
    private val intervalRecords: Map<FinancialUnit, Map<LocalDate, List<ReportRecord>>>

    init {
        val (previousRecords, intervalRecords) =
            splitRecordsBeforeAndDuring(reportRecords, granularInterval)
        this.previousRecords = previousRecords
        this.intervalRecords = intervalRecords
            .mapValues { (_, records) ->
                records.groupBy {
                    maxOf(getTimeBucket(it.date, granularInterval.granularity), granularInterval.interval.from)
                }
            }
    }

    fun getRecordsByBucket(date: LocalDate): Map<FinancialUnit, List<ReportRecord>> {
        return intervalRecords.mapValues { (_, recordsByBucket) ->
            recordsByBucket[date] ?: emptyList()
        }
    }

    private fun splitRecordsBeforeAndDuring(
        records: List<ReportRecord>,
        interval: GranularDateInterval,
    ): Pair<Map<FinancialUnit, List<ReportRecord>>, Map<FinancialUnit, List<ReportRecord>>> {
        return records.partition { it.date < interval.interval.from }
            .let { (previous, interval) ->
                groupByUnit(previous) to groupByUnit(interval)
            }
    }

    private fun groupByUnit(records: List<ReportRecord>): Map<FinancialUnit, List<ReportRecord>> {
        return records.groupBy { it.unit }
            .mapValues { it.value.sortedBy { record -> record.date } }
    }
}
