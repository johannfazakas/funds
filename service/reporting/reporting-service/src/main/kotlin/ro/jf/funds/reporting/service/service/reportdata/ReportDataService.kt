package ro.jf.funds.reporting.service.service.reportdata

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.FundTransactionFilterTO
import ro.jf.funds.fund.api.model.FundTransactionTO
import ro.jf.funds.fund.sdk.FundTransactionSdk
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
    private val fundTransactionSdk: FundTransactionSdk,
    private val resolverRegistry: ReportDataResolverRegistry,
) {
    suspend fun getNetReport(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<NetReport> = withSuspendingSpan {
        getData(userId, reportViewId, interval, { it.net.enabled }, ::getNetReport)
    }

    suspend fun getGroupedNetReport(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<ByGroup<NetReport>> = withSuspendingSpan {
        getData(userId, reportViewId, interval, { it.groupedNet.enabled }, ::getGroupedNetReport)
    }

    suspend fun getValueReport(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<ValueReport> = withSuspendingSpan {
        getData(userId, reportViewId, interval, { it.valueReport.enabled }, ::getValueReport)
    }

    suspend fun getGroupedBudgetReport(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<ByGroup<Budget>> = withSuspendingSpan {
        getData(userId, reportViewId, interval, { it.groupedBudget.enabled }, ::getGroupedBudgetReport)
    }

    suspend fun getPerformanceReport(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<PerformanceReport> = withSuspendingSpan {
        getData(userId, reportViewId, interval, { it.performance.enabled }, ::getPerformanceData)
    }

    private suspend fun <T> getData(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
        featureCheck: (ReportsConfiguration) -> Boolean,
        dataRetriever: suspend (ReportView, ReportDataInterval, RecordStore) -> ReportData<T>,
    ): ReportData<T> {
        return coroutineScope {
            val reportView = reportViewRepository.findById(userId, reportViewId)
                ?: throw ReportViewNotFound(userId, reportViewId)
            if (!featureCheck(reportView.dataConfiguration.reports)) {
                throw FeatureDisabled(userId, reportView.id)
            }
            val recordStore = createRecordStore(reportView, interval)
            dataRetriever.invoke(reportView, interval, recordStore)
        }
    }

    private fun CoroutineScope.createRecordStore(
        reportView: ReportView,
        interval: ReportDataInterval,
    ): RecordStore = RecordStore(
        previousRecords = async { getPreviousRecords(reportView, interval) },
        bucketRecords = interval.getBuckets()
            .map { bucket -> bucket to async { getBucketRecords(reportView, bucket) } }.toMap(),
    )

    // TODO(Johann) should these be extracted
    private suspend fun getBucketRecords(reportView: ReportView, timeBucket: TimeBucket): List<ReportRecord> =
        getReportRecords(reportView, timeBucket.from, timeBucket.to)

    private suspend fun getPreviousRecords(reportView: ReportView, interval: ReportDataInterval): List<ReportRecord> =
        getReportRecords(reportView, null, interval.getPreviousLastDay())

    // TODO(Johann) keep in mind that only grouped budget and net reportdata require previous records.
    private suspend fun getReportRecords(
        reportView: ReportView,
        fromDate: LocalDate?,
        toDate: LocalDate?,
    ): List<ReportRecord> = withSuspendingSpan {
        val filter = FundTransactionFilterTO(fromDate, toDate)
        fundTransactionSdk
            .listTransactions(reportView.userId, reportView.fundId, filter).items
            .asSequence()
            .flatMap { it.toReportRecords(reportView) }
            .toList()
    }

    private fun FundTransactionTO.toReportRecords(
        reportView: ReportView,
    ): List<ReportRecord> {
        return this.records
            .filter { record -> record.fundId == reportView.fundId }
            .map { record ->
                ReportRecord(
                    transactionId = this.id,
                    date = this.dateTime.date,
                    unit = record.unit,
                    amount = record.amount,
                    labels = record.labels,
                )
            }
    }

    private suspend fun getNetReport(
        reportView: ReportView,
        interval: ReportDataInterval,
        recordStore: RecordStore,
    ): ReportData<NetReport> = withSuspendingSpan {
        val input = ReportDataResolverInput(reportView.userId, interval, recordStore, reportView.dataConfiguration)
        val netData = resolveNetData(input) ?: error("Net reportdata could not be found")
        generateReportData(reportView.id, interval) { timeBucket -> netData[timeBucket] }
    }

    private suspend fun getGroupedNetReport(
        reportView: ReportView,
        interval: ReportDataInterval,
        recordStore: RecordStore,
    ): ReportData<ByGroup<NetReport>> = withSuspendingSpan {
        val input = ReportDataResolverInput(reportView.userId, interval, recordStore, reportView.dataConfiguration)
        val groupedNetData = resolveGroupedNetData(input) ?: error("Grouped net reportdata could not be found")
        generateReportData(reportView.id, interval) { timeBucket -> groupedNetData[timeBucket] }
    }

    private suspend fun getValueReport(
        reportView: ReportView,
        interval: ReportDataInterval,
        recordStore: RecordStore,
    ): ReportData<ValueReport> = withSuspendingSpan {
        val input = ReportDataResolverInput(reportView.userId, interval, recordStore, reportView.dataConfiguration)
        val valueData = resolveValueReportData(input) ?: error("Value reportdata could not be found")
        generateReportData(reportView.id, interval) { timeBucket -> valueData[timeBucket] }
    }

    private suspend fun getGroupedBudgetReport(
        reportView: ReportView,
        interval: ReportDataInterval,
        recordStore: RecordStore,
    ): ReportData<ByGroup<Budget>> = withSuspendingSpan {
        val input = ReportDataResolverInput(reportView.userId, interval, recordStore, reportView.dataConfiguration)
        val groupedBudgetData = resolveGroupedBudgetData(input) ?: error("Grouped budget reportdata could not be found")
        generateReportData(reportView.id, interval) { timeBucket -> groupedBudgetData[timeBucket] }
    }

    private suspend fun getPerformanceData(
        reportView: ReportView,
        interval: ReportDataInterval,
        recordStore: RecordStore,
    ): ReportData<PerformanceReport> = withSuspendingSpan {
        val input = ReportDataResolverInput(reportView.userId, interval, recordStore, reportView.dataConfiguration)
        val performanceData = resolvePerformanceReportData(input) ?: error("Performance reportdata could not be found")
        generateReportData(reportView.id, interval) { timeBucket -> performanceData[timeBucket] }
    }

    private fun <T> generateReportData(
        reportViewId: UUID,
        interval: ReportDataInterval,
        bucketDataSupplier: (TimeBucket) -> T?,
    ): ReportData<T> =
        ReportData(
            reportViewId = reportViewId,
            interval = interval,
            buckets = sequenceOf(
                interval.getBuckets().map { it to BucketType.REAL },
                interval.getForecastBuckets().map { it to BucketType.FORECAST }
            )
                .flatten()
                .map { (bucket, bucketType) ->
                    BucketData<T>(
                        timeBucket = bucket,
                        bucketType = bucketType,
                        report = bucketDataSupplier(bucket) ?: error("Bucket reportdata could not be found"),
                    )
                }
                .toList<BucketData<T>>()
        )

    private suspend fun resolveNetData(input: ReportDataResolverInput): ByBucket<NetReport>? {
        return resolveRealAndForecastData(resolverRegistry.net, input)
    }

    private suspend fun resolveGroupedNetData(input: ReportDataResolverInput): ByBucket<ByGroup<NetReport>>? {
        return resolveRealAndForecastData(resolverRegistry.groupedNet, input)
    }

    private suspend fun resolveValueReportData(input: ReportDataResolverInput): ByBucket<ValueReport>? {
        return resolveRealAndForecastData(resolverRegistry.valueReport, input)
    }

    private suspend fun resolveGroupedBudgetData(input: ReportDataResolverInput): ByBucket<ByGroup<Budget>>? {
        return resolveRealAndForecastData(resolverRegistry.groupedBudget, input)
    }

    private suspend fun resolvePerformanceReportData(input: ReportDataResolverInput): ByBucket<PerformanceReport>? {
        return resolveRealAndForecastData(resolverRegistry.performanceReport, input)
    }

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
