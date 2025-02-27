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
import ro.jf.funds.reporting.api.model.ReportDataConfigurationTO
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
            userId,
            payload.name,
            payload.fundId,
            payload.dataConfiguration.toModel()
        )

        val transactions = fundTransactionSdk.listTransactions(userId, payload.fundId).items
        persistReportRecords(userId, reportView.id, transactions, payload.fundId, payload.dataConfiguration.currency)

        return reportView
    }

    suspend fun getReportView(userId: UUID, reportViewId: UUID): ReportView {
        return reportViewRepository.findById(userId, reportViewId)
            ?: throw ReportingException.ReportViewNotFound(userId, reportViewId)
    }

    suspend fun deleteReportView(userId: UUID, reportViewId: UUID) {
        reportViewRepository.delete(userId, reportViewId)
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

        transactions
            .asSequence()
            .flatMap { transaction ->
                transaction.records
                    .filter { it.fundId == fundId }
                    .map { record ->
                        CreateReportRecordCommand(
                            userId = userId,
                            reportViewId = reportViewId,
                            recordId = record.id,
                            date = transaction.dateTime.date,
                            unit = record.unit,
                            amount = record.amount,
                            reportCurrencyAmount = record.amount * getConversionRate(
                                record, currency, conversions, transaction.dateTime.date
                            ),
                            labels = record.labels
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

    // TODO(Johann-11) maybe TOs could be removed completely from the service. but I'll probably need serialization annotations on the model
    private fun ReportDataConfigurationTO.toModel() =
        ReportDataConfiguration(
            currency = currency,
            filter = RecordFilter(filter.labels),
            groups = groups?.map { ReportGroup(it.name, RecordFilter(it.filter.labels)) },
            features = ReportDataFeaturesConfiguration(
                net = NetReportFeature(features.net.enabled, features.net.applyFilter),
                valueReport = GenericReportFeature(features.valueReport.enabled),
            ),
        )
}
