package ro.jf.funds.reporting.service.service.data

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
import ro.jf.funds.reporting.service.service.data.resolver.ReportDataForecastInput
import ro.jf.funds.reporting.service.service.data.resolver.ReportDataResolver
import ro.jf.funds.reporting.service.service.data.resolver.ReportDataResolverInput
import ro.jf.funds.reporting.service.service.data.resolver.ReportDataResolverRegistry
import java.math.BigDecimal
import java.util.*

private val log = logger { }

class ReportDataService(
    private val reportViewRepository: ReportViewRepository,
    private val fundTransactionSdk: FundTransactionSdk,
    private val resolverRegistry: ReportDataResolverRegistry,
) {
    // TODO(Johann) might remove this endpoint containing all
    suspend fun getReportViewData(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<ReportDataAggregate> = withSuspendingSpan {
        coroutineScope {
            val reportView = reportViewRepository.findById(userId, reportViewId)
                ?: throw ReportingException.ReportViewNotFound(userId, reportViewId)
            val recordStore = createRecordStore(reportView, interval)
            getReportData(reportView, interval, recordStore)
        }
    }

    // TODO(Johann) I think this shouldn't return a <BigDecimal>, it could be wrapped.
    suspend fun getNetData(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<BigDecimal> = withSuspendingSpan {
        getData(userId, reportViewId, interval, { it.net.enabled }, ::getNetData)
    }

    suspend fun getGroupedNetData(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<ByGroup<BigDecimal>> = withSuspendingSpan {
        getData(userId, reportViewId, interval, { it.groupedNet.enabled }, ::getGroupedNetData)
    }

    suspend fun getValueData(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<ValueReport> = withSuspendingSpan {
        getData(userId, reportViewId, interval, { it.valueReport.enabled }, ::getValueData)
    }

    suspend fun getGroupedBudgetData(
        userId: UUID,
        reportViewId: UUID,
        interval: ReportDataInterval,
    ): ReportData<ByGroup<Budget>> = withSuspendingSpan {
        getData(userId, reportViewId, interval, { it.groupedBudget.enabled }, ::getGroupedBudgetData)
    }

    suspend fun getPerformanceData(
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

    // TODO(Johann) keep in mind that only grouped budget and net data require previous records.
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

    private suspend fun getReportData(
        reportView: ReportView,
        interval: ReportDataInterval,
        recordStore: RecordStore,
    ): ReportData<ReportDataAggregate> = withSuspendingSpan {
        coroutineScope {
            val realInput = ReportDataResolverInput(
                reportView.userId, interval, recordStore, reportView.dataConfiguration
            )

            val netDataDeferred = async { resolveNetData(realInput) }
            val groupedNetDataDeferred = async { resolveGroupedNetData(realInput) }
            val valueReportDataDeferred = async { resolveValueReportData(realInput) }
            val groupedBudgetDataDeferred = async { resolveGroupedBudgetData(realInput) }
            val performanceReportDataDeferred = async { resolvePerformanceReportData(realInput) }

            val netData = netDataDeferred.await()
            val groupedNetData = groupedNetDataDeferred.await()
            val valueReportData = valueReportDataDeferred.await()
            val groupedBudgetData = groupedBudgetDataDeferred.await()
            val performanceReportData = performanceReportDataDeferred.await()

            generateReportData(reportView.id, interval) { timeBucket ->
                ReportDataAggregate(
                    net = netData?.get(timeBucket),
                    groupedNet = groupedNetData?.get(timeBucket),
                    groupedBudget = groupedBudgetData?.get(timeBucket),
                    value = valueReportData?.get(timeBucket),
                    performance = performanceReportData?.get(timeBucket)
                )
            }
        }
    }

    private suspend fun getNetData(
        reportView: ReportView,
        interval: ReportDataInterval,
        recordStore: RecordStore,
    ): ReportData<BigDecimal> = withSuspendingSpan {
        val input = ReportDataResolverInput(reportView.userId, interval, recordStore, reportView.dataConfiguration)
        val netData = resolveNetData(input) ?: error("Net data could not be found")
        generateReportData(reportView.id, interval) { timeBucket -> netData[timeBucket] }
    }

    private suspend fun getGroupedNetData(
        reportView: ReportView,
        interval: ReportDataInterval,
        recordStore: RecordStore,
    ): ReportData<ByGroup<BigDecimal>> = withSuspendingSpan {
        val input = ReportDataResolverInput(reportView.userId, interval, recordStore, reportView.dataConfiguration)
        val groupedNetData = resolveGroupedNetData(input) ?: error("Grouped net data could not be found")
        generateReportData(reportView.id, interval) { timeBucket -> groupedNetData[timeBucket] }
    }

    private suspend fun getValueData(
        reportView: ReportView,
        interval: ReportDataInterval,
        recordStore: RecordStore,
    ): ReportData<ValueReport> = withSuspendingSpan {
        val input = ReportDataResolverInput(reportView.userId, interval, recordStore, reportView.dataConfiguration)
        val valueData = resolveValueReportData(input) ?: error("Value data could not be found")
        generateReportData(reportView.id, interval) { timeBucket -> valueData[timeBucket] }
    }

    private suspend fun getGroupedBudgetData(
        reportView: ReportView,
        interval: ReportDataInterval,
        recordStore: RecordStore,
    ): ReportData<ByGroup<Budget>> = withSuspendingSpan {
        val input = ReportDataResolverInput(reportView.userId, interval, recordStore, reportView.dataConfiguration)
        val groupedBudgetData = resolveGroupedBudgetData(input) ?: error("Grouped budget data could not be found")
        generateReportData(reportView.id, interval) { timeBucket -> groupedBudgetData[timeBucket] }
    }

    private suspend fun getPerformanceData(
        reportView: ReportView,
        interval: ReportDataInterval,
        recordStore: RecordStore,
    ): ReportData<PerformanceReport> = withSuspendingSpan {
        val input = ReportDataResolverInput(reportView.userId, interval, recordStore, reportView.dataConfiguration)
        val performanceData = resolvePerformanceReportData(input) ?: error("Performance data could not be found")
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
            data = sequenceOf(
                interval.getBuckets().map { it to BucketType.REAL },
                interval.getForecastBuckets().map { it to BucketType.FORECAST }
            )
                .flatten()
                .map { (bucket, bucketType) ->
                    BucketData<T>(
                        timeBucket = bucket,
                        bucketType = bucketType,
                        data = bucketDataSupplier(bucket) ?: error("Bucket data could not be found"),
                    )
                }
                .toList<BucketData<T>>()
        )

    private suspend fun resolveNetData(input: ReportDataResolverInput): ByBucket<BigDecimal>? {
        return resolveRealAndForecastData(resolverRegistry.net, input)
    }

    private suspend fun resolveGroupedNetData(input: ReportDataResolverInput): ByBucket<ByGroup<BigDecimal>>? {
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
