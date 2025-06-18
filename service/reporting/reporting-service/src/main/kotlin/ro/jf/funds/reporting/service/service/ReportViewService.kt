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
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.utils.withSuspendingSpan
import java.math.BigDecimal
import java.util.*

private val log = logger { }

class ReportViewService(
    private val reportViewRepository: ReportViewRepository,
    private val reportRecordRepository: ReportRecordRepository,
    private val fundTransactionSdk: FundTransactionSdk,
    private val historicalPricingSdk: HistoricalPricingSdk,
) {
    suspend fun createReportView(userId: UUID, payload: CreateReportViewCommand): ReportView = withSuspendingSpan {
        log.info { "Create report view for user $userId: $payload" }

        reportViewRepository.findByName(userId, payload.name)?.let {
            throw ReportingException.ReportViewAlreadyExists(userId, payload.name)
        }
        validateCreateReportViewRequest(payload, userId)
        val reportView = reportViewRepository.save(payload)

        val transactions = fundTransactionSdk.listTransactions(userId, payload.fundId).items
        persistReportRecords(
            userId,
            reportView.id,
            transactions,
            payload.fundId,
            payload.dataConfiguration.currency
        )

        reportView
    }

    suspend fun getReportView(userId: UUID, reportViewId: UUID): ReportView = withSuspendingSpan {
        reportViewRepository.findById(userId, reportViewId)
            ?: throw ReportingException.ReportViewNotFound(userId, reportViewId)
    }


    suspend fun deleteReportView(userId: UUID, reportViewId: UUID) = withSuspendingSpan {
        reportViewRepository.delete(userId, reportViewId)
    }

    suspend fun listReportViews(userId: UUID): List<ReportView> = withSuspendingSpan {
        reportViewRepository.findAll(userId)
    }

    private suspend fun persistReportRecords(
        userId: UUID,
        reportViewId: UUID,
        transactions: List<FundTransactionTO>,
        fundId: UUID,
        currency: Currency,
    ) = withSuspendingSpan {
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

    private fun validateCreateReportViewRequest(payload: CreateReportViewCommand, userId: UUID) {
        val dataConfiguration = payload.dataConfiguration
        validateGroupedNetFeature(dataConfiguration, userId)
        validateGroupedBudgetFeature(dataConfiguration, userId)
    }

    private fun validateGroupedNetFeature(dataConfiguration: ReportDataConfiguration, userId: UUID) {
        if (dataConfiguration.features.groupedNet.enabled && dataConfiguration.groups.isNullOrEmpty()) {
            throw ReportingException.MissingGroupsRequiredForFeature(userId, "groupedNet")
        }
    }

    private fun validateGroupedBudgetFeature(dataConfiguration: ReportDataConfiguration, userId: UUID) {
        val groupedBudget = dataConfiguration.features.groupedBudget
        if (!groupedBudget.enabled) {
            return
        }
        if (dataConfiguration.groups.isNullOrEmpty()) {
            throw ReportingException.MissingGroupsRequiredForFeature(userId, "groupedBudget")
        }
        if (groupedBudget.distributions.isEmpty()) {
            throw ReportingException.MissingGroupBudgetDistributions(userId)
        }
        if (groupedBudget.distributions.count { it.default } != 1) {
            throw ReportingException.NoUniqueGroupBudgetDefaultDistribution(userId)
        }
        if (groupedBudget.distributions.any { !it.default && it.from == null }) {
            throw ReportingException.MissingStartYearMonthOnGroupBudgetDistribution(userId)
        }
        if (groupedBudget.distributions.mapNotNull { it.from }.groupBy { it }.any { it.value.size > 1 }) {
            throw ReportingException.ConflictingStartYearMonthOnGroupBudgetDistribution(userId)
        }
        val groupNames = dataConfiguration.groups.map { it.name }.sorted()
        if (groupedBudget.distributions.any { it.groups.map { group -> group.group }.sorted() != groupNames }) {
            throw ReportingException.GroupBudgetDistributionGroupsDoNotMatch(userId)
        }
        groupedBudget.distributions
            .map { it to it.groups.sumOf { group -> group.percentage } }
            .firstOrNull { (_, sum) -> sum != 100 }
            ?.let { (distribution, sum) ->
                throw ReportingException.GroupBudgetPercentageSumInvalid(userId, distribution.from, sum)
            }
    }
}
