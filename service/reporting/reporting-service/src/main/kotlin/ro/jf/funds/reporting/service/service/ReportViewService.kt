package ro.jf.funds.reporting.service.service

import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.fund.api.model.FundRecordTO
import ro.jf.funds.fund.api.model.FundTransactionTO
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.api.model.getTimeBucket
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import java.math.BigDecimal
import java.util.*

private val log = logger { }

class ReportViewService(
    private val reportViewRepository: ReportViewRepository,
    private val reportRecordRepository: ReportRecordRepository,
    private val fundTransactionSdk: FundTransactionSdk,
    private val historicalPricingSdk: HistoricalPricingSdk,
) {
    suspend fun createReportView(userId: UUID, payload: CreateReportViewTO): ReportView {
        log.info { "Create report view for user $userId: $payload" }

        reportViewRepository.findByName(userId, payload.name)?.let {
            throw ReportingException.ReportViewAlreadyExists(userId, payload.name)
        }
        val reportView = reportViewRepository.save(
            userId, payload.name, payload.fundId, payload.type, payload.currency, payload.labels
        )

        val transactions = fundTransactionSdk.listTransactions(userId, payload.fundId).items
        persistReportRecords(userId, reportView.id, transactions, payload.fundId, payload.currency)

        return reportView
    }

    suspend fun getReportView(userId: UUID, reportViewId: UUID): ReportView {
        return reportViewRepository.findById(userId, reportViewId)
            ?: throw ReportingException.ReportViewNotFound(userId, reportViewId)
    }

    suspend fun deleteReportView(userId: UUID, reportViewId: UUID) {
        reportViewRepository.delete(userId, reportViewId)
    }

    suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        granularInterval: GranularDateInterval,
    ): ReportData {
        // TODO(Johann) dive into logging a bit. how can it be controlled in a ktor service? This should probably be a DEBUG
        log.info { "Get report view data for user $userId, report $reportViewId and interval $granularInterval" }
        val reportView = reportViewRepository.findById(userId, reportViewId)
        reportView
            ?: throw ReportingException.ReportViewNotFound(userId, reportViewId)

        val reportRecords = reportRecordRepository
            .findByViewInInterval(userId, reportViewId, granularInterval.interval)
        log.info { "Found ${reportRecords.size} records for report $reportViewId in interval ${granularInterval.interval}" }
        val reportRecordsByBucket = reportRecords
            .groupBy { getTimeBucket(it.date, granularInterval.granularity) }

        val dataBuckets = granularInterval
            .getTimeBuckets()
            .map { timeBucket ->
                ExpenseReportDataBucket(
                    timeBucket,
                    reportRecordsByBucket[timeBucket]
                        ?.filter { it.labels.any { label -> label in reportView.labels } }
                        ?.sumOf { it.reportCurrencyAmount }
                        ?: BigDecimal.ZERO
                )
            }

        return ExpenseReportData(reportViewId, granularInterval, dataBuckets)
    }

    suspend fun listReportViews(userId: UUID): List<ReportView> {
        return reportViewRepository.findAll(userId)
    }

    private suspend fun persistReportRecords(
        userId: UUID,
        reportViewId: UUID,
        transactions: List<FundTransactionTO>,
        fundId: UUID,
        currency: Currency,
    ) {
        val conversions = getConversions(userId, transactions, currency)

        // TODO(Johann) could be done in a single call to the db
        transactions
            .asSequence()
            .flatMap { transaction ->
                transaction.records
                    .filter { it.fundId == fundId }
                    .map {
                        CreateReportRecordCommand(
                            userId = userId,
                            reportViewId = reportViewId,
                            date = transaction.dateTime.date,
                            unit = it.unit,
                            amount = it.amount,
                            reportCurrencyAmount = it.amount * getConversionRate(
                                it, currency, conversions, transaction.dateTime.date
                            ),
                            labels = it.labels
                        )
                    }
            }
            .toList()
            .let { reportRecordRepository.saveAll(it) }
    }

    private suspend fun getConversions(
        userId: UUID,
        transactions: List<FundTransactionTO>,
        currency: Currency,
    ): ConversionsResponse {
        return transactions
            .asSequence()
            .flatMap { transaction ->
                transaction.records
                    .filter { it.unit != currency }
                    .map { record -> ConversionRequest(record.unit, currency, transaction.dateTime.date) }
            }
            .distinct()
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.let { historicalPricingSdk.convert(userId, ConversionsRequest(it)) }
            ?: ConversionsResponse(emptyList())
    }

    private fun getConversionRate(
        record: FundRecordTO,
        currency: Currency,
        conversions: ConversionsResponse,
        date: LocalDate,
    ): BigDecimal = if (record.unit == currency)
        BigDecimal.ONE
    else
        conversions.getRate(record.unit, currency, date)
            ?: throw ReportingException.ReportRecordConversionRateNotFound(record.id)
}
