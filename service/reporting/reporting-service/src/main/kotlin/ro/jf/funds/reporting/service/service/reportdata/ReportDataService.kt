package ro.jf.funds.reporting.service.service.reportdata

import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.service.getBuckets
import ro.jf.funds.reporting.service.service.reportdata.resolver.ReportDataResolverInput
import ro.jf.funds.reporting.service.service.reportdata.resolver.ReportDataResolverRegistry
import java.math.BigDecimal
import java.util.*
// TODO(Johann) measuring time could be done using prometheus maybe
import kotlin.time.measureTimedValue

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
    ): ReportData {
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
        return ReportData(reportViewId, granularInterval, data)
    }

    private fun getReportDataAggregates(
        granularInterval: GranularDateInterval,
        catalog: RecordCatalog,
        conversions: ConversionsResponse,
        reportDataConfiguration: ReportDataConfiguration,
    ): List<BucketData<ReportDataAggregate>> {
        val input = ReportDataResolverInput(granularInterval, catalog, conversions, reportDataConfiguration)

        val netData = resolveNetData(input)
        val groupedNetData = resolveGroupedNetData(input)
        val valueReportData = resolveValueReportData(input)
        val groupedBudgetData = resolveGroupedBudgetData(input)

        return granularInterval.getBuckets()
            .map { bucket ->
                BucketData(
                    timeBucket = bucket,
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
    ): ConversionsResponse = measureTimedValue {
        val sourceUnits = reportRecords
            .asSequence()
            .map { it.unit }
            .filter { it != targetUnit }
            .distinct()
            .toList()

        val conversionsRequest = sequenceOf(
            getBucketBoundingConversionRequests(sourceUnits, targetUnit, granularInterval),
            getRecordConversionRequests(sourceUnits, targetUnit, reportRecords)
        )
            .flatten().distinct().toList().let(::ConversionsRequest)
        historicalPricingSdk.convert(userId, conversionsRequest)
    }.let { (result, duration) ->
        log.debug { "Conversions resolved in $duration" }
        result
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
        val (netData, netDataDuration) =
            measureTimedValue { resolverRegistry.net.resolve(input) }
        if (netData != null) {
            log.debug { "Net data resolved in $netDataDuration" }
        }
        return netData
    }

    private fun resolveGroupedNetData(input: ReportDataResolverInput): ByBucket<ByGroup<BigDecimal>>? {
        val (groupedNetData, groupedNetDataDuration) =
            measureTimedValue { resolverRegistry.groupedNet.resolve(input) }
        if (groupedNetData != null) {
            log.debug { "Grouped net data resolved in $groupedNetDataDuration" }
        }
        return groupedNetData
    }

    private fun resolveValueReportData(input: ReportDataResolverInput): ByBucket<ValueReport>? {
        val (valueReportData, valueReportDataDuration) =
            measureTimedValue { resolverRegistry.valueReport.resolve(input) }
        if (valueReportData != null) {
            log.debug { "Value report data resolved in $valueReportDataDuration" }
        }
        return valueReportData
    }

    private fun resolveGroupedBudgetData(input: ReportDataResolverInput): ByBucket<ByGroup<ByUnit<Budget>>>? {
        val (groupedBudgetData, groupedBudgetDataDuration) =
            measureTimedValue { resolverRegistry.groupedBudget.resolve(input) }
        if (groupedBudgetData != null) {
            log.debug { "Grouped budget data resolved in $groupedBudgetDataDuration" }
        }
        return groupedBudgetData
    }
}
