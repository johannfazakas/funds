package ro.jf.funds.reporting.service.service.reportdata

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging.logger
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.domain.ReportingException.FeatureDisabled
import ro.jf.funds.reporting.service.domain.ReportingException.ReportViewNotFound
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.service.reportdata.resolver.ReportDataForecastInput
import ro.jf.funds.reporting.service.service.reportdata.resolver.ReportDataResolver
import ro.jf.funds.reporting.service.service.reportdata.resolver.ReportDataResolverInput
import ro.jf.funds.reporting.service.service.reportdata.resolver.ReportDataResolverRegistry
import java.util.*

private val log = logger { }

class ReportDataService(
    private val reportViewRepository: ReportViewRepository,
    private val resolverRegistry: ReportDataResolverRegistry,
    private val transactionService: ReportTransactionService,
) {
    suspend fun getNetReport(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<NetReport> = withSuspendingSpan {
        getData(userId, reportViewId, interval, resolverRegistry.net, ReportsConfiguration::net)
    }

    suspend fun getGroupedNetReport(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<ByGroup<NetReport>> = withSuspendingSpan {
        getData(userId, reportViewId, interval, resolverRegistry.groupedNet, ReportsConfiguration::groupedNet)
    }

    suspend fun getValueReport(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<ValueReport> = withSuspendingSpan {
        getData(userId, reportViewId, interval, resolverRegistry.valueReport, ReportsConfiguration::valueReport)
    }

    suspend fun getGroupedBudgetReport(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<ByGroup<Budget>> = withSuspendingSpan {
        getData(userId, reportViewId, interval, resolverRegistry.groupedBudget, ReportsConfiguration::groupedBudget)
    }

    suspend fun getPerformanceReport(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<PerformanceReport> = withSuspendingSpan {
        getData(userId, reportViewId, interval, resolverRegistry.performanceReport, ReportsConfiguration::performance)
    }

    private suspend fun <T> getData(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
        resolver: ReportDataResolver<T>,
        reportConfigurationFunction: (ReportsConfiguration) -> ReportConfiguration,
    ): ReportData<T> {
        return coroutineScope {
            val reportView = reportViewRepository.findById(userId, reportViewId)
                ?: throw ReportViewNotFound(userId, reportViewId)
            if (!reportConfigurationFunction(reportView.dataConfiguration.reports).enabled) {
                throw FeatureDisabled(userId, reportView.id)
            }
            val recordStore = createRecordStore(reportView, interval)
            val input = ReportDataResolverInput(reportView, interval, recordStore)
            getReportData(resolver, input)
        }
    }

    private fun CoroutineScope.createRecordStore(
        reportView: ReportView,
        interval: ReportDataInterval,
    ): RecordStore = RecordStore(
        // TODO(Johann) keep in mind that only grouped budget and net data require previous records.
        previousRecords = async {
            transactionService.getPreviousReportRecords(reportView, interval)
        },
        bucketRecords = interval.getBuckets()
            .map { bucket ->
                bucket to async {
                    transactionService.getBucketReportRecords(reportView, bucket)
                }
            }
            .toMap(),
    )

    private suspend fun <T> getReportData(
        resolver: ReportDataResolver<T>,
        input: ReportDataResolverInput,
    ): ReportData<T> {
        val reportData = resolveRealAndForecastData(resolver, input)
            ?: error("ReportData could not be found")
        return generateReportData(input) { timeBucket -> reportData[timeBucket] }
    }

    private fun <T> generateReportData(
        input: ReportDataResolverInput,
        bucketDataSupplier: (TimeBucket) -> T?,
    ): ReportData<T> =
        ReportData(
            reportViewId = input.reportView.id,
            interval = input.interval,
            buckets = sequenceOf(
                input.interval.getBuckets().map { it to BucketType.REAL },
                input.interval.getForecastBuckets().map { it to BucketType.FORECAST }
            )
                .flatten()
                .map { (bucket, bucketType) ->
                    BucketData<T>(
                        timeBucket = bucket,
                        bucketType = bucketType,
                        report = bucketDataSupplier(bucket) ?: error("Bucket data could not be found"),
                    )
                }
                .toList<BucketData<T>>()
        )

    // TODO(Johann) this shouldn't be nullable I guess
    private suspend fun <T> resolveRealAndForecastData(
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
