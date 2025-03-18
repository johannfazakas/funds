package ro.jf.funds.reporting.service.service.reportdata

import mu.KotlinLogging.logger
import ro.jf.funds.commons.model.Currency
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
import java.util.*

private val log = logger { }

// TODO(Johann-14) refactor all classes
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

        val netData = resolverRegistry.net.resolve(input)
        val groupedNetData = resolverRegistry.groupedNet.resolve(input)
        val valueReportData = resolverRegistry.valueReport.resolve(input)
        val groupedBudgetData = resolverRegistry.groupedBudget.resolve(input)

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
    ): ConversionsResponse {
        val sourceUnits = reportRecords
            .asSequence()
            .map { it.unit }
            .filter { it != targetUnit }
            .distinct().toList()

        val dates = granularInterval.getBuckets().flatMap { listOf(it.from, it.to) }.toList()

        val conversionsRequest = ConversionsRequest(
            conversions = sourceUnits
                .asSequence()
                .flatMap { sourceUnit -> dates.map { sourceUnit to it } }
                .map { (sourceUnit, date) ->
                    ConversionRequest(sourceUnit, targetUnit, date)
                }
                .toList()
        )
        return historicalPricingSdk.convert(userId, conversionsRequest)
    }
}
