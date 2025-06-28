package ro.jf.funds.reporting.service.service.reportdata

import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.api.model.YearMonth
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.service.getBuckets
import ro.jf.funds.reporting.service.service.getForecastBuckets
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
    private val reportRecordRepository: ReportRecordRepository,
    private val historicalPricingSdk: HistoricalPricingSdk,
    private val resolverRegistry: ReportDataResolverRegistry,
) {
    suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        granularInterval: GranularDateInterval,
    ): ReportData = withSuspendingSpan {
        // TODO(Johann) dive into logging a bit. how can it be controlled in a ktor service? This should probably be a DEBUG
        log.info { "Get report view data for user $userId, report $reportViewId and interval $granularInterval" }

        val reportView = reportViewRepository.findById(userId, reportViewId)
            ?: throw ReportingException.ReportViewNotFound(userId, reportViewId)
        val reportRecords = reportRecordRepository
            .findByViewUntil(userId, reportViewId, granularInterval.interval.to)
        log.info { "Found ${reportRecords.size} records for report $reportViewId in interval ${granularInterval.interval}" }
        val catalog = RecordCatalog(reportRecords, granularInterval)
        val reportDataConfiguration = reportView.dataConfiguration
        val conversions = getConversions(userId, reportDataConfiguration.currency, reportRecords, granularInterval)
        val data = getReportDataAggregates(granularInterval, catalog, conversions, reportDataConfiguration)
        ReportData(reportViewId, granularInterval, data)
    }

    private fun getReportDataAggregates(
        granularInterval: GranularDateInterval,
        catalog: RecordCatalog,
        conversions: ConversionsResponse,
        reportDataConfiguration: ReportDataConfiguration,
    ): List<BucketData<ReportDataAggregate>> = withSpan("getReportDataAggregates") {
        val realInput = ReportDataResolverInput(granularInterval, catalog, conversions, reportDataConfiguration)

        val netData = resolveNetData(realInput)
        val groupedNetData = resolveGroupedNetData(realInput)
        val valueReportData = resolveValueReportData(realInput)
        val groupedBudgetData = resolveGroupedBudgetData(realInput)

        val forecastBuckets = reportDataConfiguration.features.forecast.outputBuckets
        sequenceOf(
            granularInterval.getBuckets().map { it to BucketType.REAL },
            granularInterval.getForecastBuckets(forecastBuckets).map { it to BucketType.FORECAST }
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
        granularInterval: GranularDateInterval,
    ): ConversionsResponse {
        val sourceUnits = reportRecords
            .asSequence()
            .map { it.unit }
            .filter { it != targetUnit }
            .distinct()
            .toList()

        val conversionsRequest = sequenceOf(
            getMonthlyConversionRequests(sourceUnits, targetUnit, granularInterval, reportRecords),
            getBucketBoundingConversionRequests(sourceUnits, targetUnit, granularInterval),
            getRecordConversionRequests(sourceUnits, targetUnit, reportRecords)
        )
            .flatten().distinct().toList().let(::ConversionsRequest)
        return historicalPricingSdk.convert(userId, conversionsRequest)
    }

    private fun getMonthlyConversionRequests(
        sourceUnits: List<FinancialUnit>,
        targetUnit: Currency,
        granularInterval: GranularDateInterval,
        reportRecords: List<ReportRecord>,
    ): List<ConversionRequest> {
        val startDate = reportRecords.minOf { it.date }
        val endDate = granularInterval.interval.to
        return generateSequence(YearMonth(startDate.year, startDate.month.value)) { it.next() }
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
        granularInterval: GranularDateInterval,
    ): List<ConversionRequest> {
        val dates = granularInterval.getBuckets().flatMap { listOf(it.from, it.to) }.toList()

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
