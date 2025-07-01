package ro.jf.funds.reporting.service.service.reportdata

import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.fund.api.model.FundTransactionFilterTO
import ro.jf.funds.fund.api.model.FundTransactionTO
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.reporting.api.model.YearMonthTO
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.service.reportdata.resolver.ReportDataForecastInput
import ro.jf.funds.reporting.service.service.reportdata.resolver.ReportDataResolver
import ro.jf.funds.reporting.service.service.reportdata.resolver.ReportDataResolverInput
import ro.jf.funds.reporting.service.service.reportdata.resolver.ReportDataResolverRegistry
import ro.jf.funds.reporting.service.utils.withSpan
import ro.jf.funds.reporting.service.utils.withSuspendingSpan
import java.math.BigDecimal
import java.util.*

private val log = logger { }

class ReportDataService(
    private val reportViewRepository: ReportViewRepository,
    private val historicalPricingSdk: HistoricalPricingSdk,
    private val fundTransactionSdk: FundTransactionSdk,
    private val resolverRegistry: ReportDataResolverRegistry,
) {
    suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData = withSuspendingSpan {
        val reportView = reportViewRepository.findById(userId, reportViewId)
            ?: throw ReportingException.ReportViewNotFound(userId, reportViewId)
        val reportRecords = getReportRecords(reportView, interval)
        log.info { "Found ${reportRecords.size} records for report $reportViewId in interval $interval" }
        val catalog = RecordCatalog(reportRecords, interval)
        val reportDataConfiguration = reportView.dataConfiguration
        val conversions = getConversions(userId, reportDataConfiguration.currency, reportRecords, interval)
        val data = getReportDataAggregates(interval, catalog, conversions, reportDataConfiguration)
        ReportData(reportViewId, interval, data)
    }

    private suspend fun getReportRecords(
        reportView: ReportView,
        interval: ReportDataInterval,
    ): List<ReportRecord> = withSuspendingSpan {
        // TODO(Johann) keep in mind that only grouped budget and net data require previous records.
        val transactionFilter = FundTransactionFilterTO(toDate = interval.toDate)
        fundTransactionSdk
            .listTransactions(reportView.userId, reportView.fundId, transactionFilter).items
            .asSequence()
            .flatMap { it.toReportRecords(reportView.userId, reportView.id) }
            .toList()
    }

    private fun FundTransactionTO.toReportRecords(
        userId: UUID,
        reportViewId: UUID,
    ): List<ReportRecord> {
        return this.records.map { record ->
            ReportRecord(
                id = UUID.randomUUID(),
                userId = userId,
                reportViewId = reportViewId,
                date = this.dateTime.date,
                unit = record.unit,
                amount = record.amount,
                recordId = record.id,
                labels = record.labels,
            )
        }
    }

    private fun getReportDataAggregates(
        interval: ReportDataInterval,
        catalog: RecordCatalog,
        conversions: ConversionsResponse,
        reportDataConfiguration: ReportDataConfiguration,
    ): List<BucketData<ReportDataAggregate>> = withSpan("getReportDataAggregates") {
        val realInput = ReportDataResolverInput(interval, catalog, conversions, reportDataConfiguration)

        val netData = resolveNetData(realInput)
        val groupedNetData = resolveGroupedNetData(realInput)
        val valueReportData = resolveValueReportData(realInput)
        val groupedBudgetData = resolveGroupedBudgetData(realInput)

        sequenceOf(
            interval.getBuckets().map { it to BucketType.REAL },
            interval.getForecastBuckets().map { it to BucketType.FORECAST }
        )
            .flatten()
            .map { (bucket, bucketType) ->
                BucketData(
                    timeBucket = bucket,
                    bucketType = bucketType,
                    aggregate = ReportDataAggregate(
                        net = netData?.get(bucket),
                        groupedNet = groupedNetData?.get(bucket),
                        groupedBudget = groupedBudgetData?.get(bucket),
                        value = valueReportData?.get(bucket),
                    )
                )
            }
            .toList()
    }

    private suspend fun getConversions(
        userId: UUID,
        targetUnit: Currency,
        reportRecords: List<ReportRecord>,
        interval: ReportDataInterval,
    ): ConversionsResponse {
        val sourceUnits = reportRecords
            .asSequence()
            .map { it.unit }
            .filter { it != targetUnit }
            .distinct()
            .toList()

        val conversionsRequest = sequenceOf(
            getMonthlyConversionRequests(reportRecords, sourceUnits, targetUnit, interval),
            getBucketBoundingConversionRequests(sourceUnits, targetUnit, interval),
            getRecordConversionRequests(sourceUnits, targetUnit, reportRecords)
        )
            .flatten().distinct().toList().let(::ConversionsRequest)
        return historicalPricingSdk.convert(userId, conversionsRequest)
    }

    private fun getMonthlyConversionRequests(
        reportRecords: List<ReportRecord>,
        sourceUnits: List<FinancialUnit>,
        targetUnit: Currency,
        interval: ReportDataInterval,
    ): List<ConversionRequest> {
        val startDate = reportRecords.minOf { it.date }
        val endDate = interval.toDate
        return generateSequence(YearMonthTO(startDate.year, startDate.month.value)) { it.next() }
            .asSequence()
            .takeWhile { it.asDateInterval().from > endDate }
            .map { it.asDateInterval().to }
            .flatMap { monthEnd ->
                sourceUnits.map { sourceUnit ->
                    ConversionRequest(sourceUnit, targetUnit, monthEnd)
                }
            }
            .toList()
    }

    private fun getRecordConversionRequests(
        sourceUnits: List<FinancialUnit>,
        targetUnit: Currency,
        reportRecords: List<ReportRecord>,
    ): List<ConversionRequest> {
        return reportRecords
            .asSequence()
            .map { record -> record.date }
            .distinct()
            .flatMap { date -> sourceUnits.map { sourceUnit -> ConversionRequest(sourceUnit, targetUnit, date) } }
            .toList()
    }

    private fun getBucketBoundingConversionRequests(
        sourceUnits: List<FinancialUnit>,
        targetUnit: Currency,
        interval: ReportDataInterval,
    ): List<ConversionRequest> {
        val dates = interval.getBuckets().flatMap { listOf(it.from, it.to) }.toList()

        return sourceUnits
            .asSequence()
            .flatMap { sourceUnit -> dates.map { sourceUnit to it } }
            .map { (sourceUnit, date) ->
                ConversionRequest(sourceUnit, targetUnit, date)
            }
            .toList()
    }

    private fun resolveNetData(input: ReportDataResolverInput): ByBucket<BigDecimal>? {
        return resolveRealAndForecastData(resolverRegistry.net, input)
    }

    private fun resolveGroupedNetData(input: ReportDataResolverInput): ByBucket<ByGroup<BigDecimal>>? {
        return resolveRealAndForecastData(resolverRegistry.groupedNet, input)
    }

    private fun resolveValueReportData(input: ReportDataResolverInput): ByBucket<ValueReport>? {
        return resolveRealAndForecastData(resolverRegistry.valueReport, input)
    }

    private fun resolveGroupedBudgetData(input: ReportDataResolverInput): ByBucket<ByGroup<Budget>>? {
        return resolveRealAndForecastData(resolverRegistry.groupedBudget, input)
    }

    private fun <T> resolveRealAndForecastData(
        resolver: ReportDataResolver<T>,
        input: ReportDataResolverInput,
    ): ByBucket<T>? =
        resolver.resolve(input)
            ?.let { realData ->
                resolver.forecast(ReportDataForecastInput.from(input, realData))
                    ?.let { forecastData ->
                        realData + forecastData
                    }
                    ?: realData
            }
}
