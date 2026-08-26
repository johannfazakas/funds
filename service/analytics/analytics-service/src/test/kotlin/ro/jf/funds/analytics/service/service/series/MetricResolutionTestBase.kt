package ro.jf.funds.analytics.service.service.series

import ro.jf.funds.analytics.service.service.MetricResolutionService
import ro.jf.funds.analytics.service.service.AnalyticsConversionService
import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.analytics.api.model.GroupingCriteria
import ro.jf.funds.analytics.service.domain.*
import ro.jf.funds.analytics.service.domain.Series
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.Category
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.platform.api.model.UnitType

abstract class MetricResolutionTestBase {
    protected val analyticsRecordRepository = mock<AnalyticsRecordRepository>()
    protected val conversionSdk = mock<ConversionSdk>()
    protected val metricResolutionService = MetricResolutionService(
        run {
            val interestRateCalculator = InterestRateCalculator()
            val conversions = AnalyticsConversionService(conversionSdk)
            val definitions: List<SeriesDefinition<*>> = listOf(
                TransactionAmountsSeriesDefinition(analyticsRecordRepository),
                OpenPositionRecordsSeriesDefinition(analyticsRecordRepository),
                InstrumentHoldingsSeriesDefinition(analyticsRecordRepository),
                CurrencyAmountsSeriesDefinition(analyticsRecordRepository),
                PairedPositionsSeriesDefinition(),
                BalanceSeriesDefinition(conversions),
                NetChangeSeriesDefinition(conversions),
                TotalInvestmentSeriesDefinition(conversions),
                CurrentInvestmentSeriesDefinition(conversions),
                TotalInstrumentValueSeriesDefinition(conversions),
                CurrencyValueSeriesDefinition(conversions),
                TotalProfitSeriesDefinition(),
                CurrentProfitSeriesDefinition(),
                TotalInterestRateSeriesDefinition(conversions, interestRateCalculator),
                CurrentInterestRateSeriesDefinition(conversions, interestRateCalculator),
            )
            val missing = Series.entries.filterNot { series -> definitions.any { it.series == series } }
            require(missing.isEmpty()) { "Series without registered definitions: $missing" }
            SeriesRegistry(definitions)
        }
    )

    protected val userId = uuid4()
    protected val fundId = uuid4()
    protected val accountId = uuid4()

    protected val openPositionFilter = AnalyticsDbRecordFilter(
        transactionTypes = listOf(TransactionType.OPEN_POSITION),
    )
    protected val instrumentFilter = AnalyticsDbRecordFilter(
        transactionTypes = listOf(TransactionType.OPEN_POSITION, TransactionType.CLOSE_POSITION),
        unitTypes = listOf(UnitType.INSTRUMENT),
    )
    protected val currencyFilter = AnalyticsDbRecordFilter(
        unitTypes = listOf(UnitType.CURRENCY),
    )
    protected val unfilteredFilter = AnalyticsDbRecordFilter()

    private val mockRates: MutableMap<Triple<FinancialUnit, Currency, LocalDate>, BigDecimal> = mutableMapOf()

    @BeforeEach
    fun setupMocks(): Unit = runBlocking {
        mockRates.clear()
        whenever(conversionSdk.convert(any())).thenAnswer { invocation ->
            val request = invocation.arguments[0] as ConversionsRequest
            ConversionsResponse(request.conversions.map { conversion ->
                val rate = if (conversion.sourceUnit == conversion.targetCurrency) BigDecimal.ONE
                else mockRates[Triple(conversion.sourceUnit, conversion.targetCurrency, conversion.date)]
                    ?: error("No mock rate configured for $conversion")
                ConversionResponse(conversion.sourceUnit, conversion.targetCurrency, conversion.date, rate)
            })
        }
        whenever(analyticsRecordRepository.getRecordsBefore(any(), any(), any()))
            .thenReturn(emptyList())
        whenever(analyticsRecordRepository.getRecords(any(), any(), any()))
            .thenReturn(emptyList())
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any(), anyOrNull()))
            .thenReturn(GroupedUnitAmounts.EMPTY)
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any(), anyOrNull()))
            .thenReturn(BucketedGroupedUnitAmounts.EMPTY)
    }

    protected fun givenRate(source: FinancialUnit, target: Currency, date: String, rate: String) {
        mockRates[Triple(source, target, LocalDate.parse(date))] = BigDecimal.parseString(rate)
    }

    protected suspend fun resolve(
        metrics: List<Series.Metric>,
        interval: ReportInterval,
        targetCurrency: Currency = Currency.RON,
        groupBy: GroupingCriteria? = null,
    ): MetricResolutionReport = metricResolutionService.resolve(
        MetricResolutionRequest(
            userId = userId,
            interval = interval,
            targetCurrency = targetCurrency,
            queries = metrics.map { metric ->
                MetricQuery(id = QueryId(metric.api.name), metric = metric, context = QueryContext(grouping = groupBy))
            },
        )
    )

    protected operator fun MetricResolutionReport.get(metric: Series.Metric): ScalarSeries =
        series.getValue(QueryId(metric.api.name))

    protected fun ungroupedAmounts(amounts: UnitAmounts) =
        GroupedUnitAmounts(mapOf(GroupKey.Ungrouped to amounts))

    protected fun ungroupedBuckets(vararg entries: Pair<LocalDateTime, UnitAmounts>) =
        BucketedGroupedUnitAmounts(entries.associate { (dateTime, amounts) ->
            dateTime to GroupedUnitAmounts(mapOf(GroupKey.Ungrouped to amounts))
        })

    protected fun analyticsRecord(
        dateTime: LocalDateTime,
        amount: String,
        unit: FinancialUnit = Currency.RON,
        transactionType: TransactionType = TransactionType.SINGLE_RECORD,
        recordFundId: Uuid = fundId,
        recordAccountId: Uuid = accountId,
        transactionId: Uuid = uuid4(),
        category: String? = null,
    ) = AnalyticsRecord(
        id = uuid4(),
        userId = userId,
        fundId = recordFundId,
        accountId = recordAccountId,
        transactionId = transactionId,
        transactionType = transactionType,
        dateTime = dateTime,
        amount = BigDecimal.parseString(amount),
        unit = unit,
        category = category?.let { Category(it) },
    )

    protected fun openPositionRecords(
        dateTime: LocalDateTime,
        currencyUnit: Currency,
        currencyAmount: String,
        instrumentUnit: Instrument,
        instrumentAmount: String,
        recordFundId: Uuid = fundId,
        recordAccountId: Uuid = accountId,
        category: String? = null,
    ): List<AnalyticsRecord> {
        val transactionId = uuid4()
        return listOf(
            analyticsRecord(
                dateTime = dateTime, amount = currencyAmount, unit = currencyUnit,
                transactionType = TransactionType.OPEN_POSITION,
                recordFundId = recordFundId, recordAccountId = recordAccountId,
                transactionId = transactionId, category = category,
            ),
            analyticsRecord(
                dateTime = dateTime, amount = instrumentAmount, unit = instrumentUnit,
                transactionType = TransactionType.OPEN_POSITION,
                recordFundId = recordFundId, recordAccountId = recordAccountId,
                transactionId = transactionId, category = category,
            ),
        )
    }

    protected fun ScalarSeries.value(
        bucket: String,
        groupKey: GroupKey = GroupKey.Ungrouped,
    ): BigDecimal = this[LocalDateTime.parse(bucket), groupKey]
}
