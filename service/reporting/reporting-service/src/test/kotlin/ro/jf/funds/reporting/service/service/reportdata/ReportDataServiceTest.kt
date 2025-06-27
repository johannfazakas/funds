package ro.jf.funds.reporting.service.service.reportdata

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import ro.jf.funds.commons.model.Currency.Companion.EUR
import ro.jf.funds.commons.model.Currency.Companion.RON
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.model.labelsOf
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.reporting.api.model.DateInterval
import ro.jf.funds.reporting.api.model.GranularDateInterval
import ro.jf.funds.reporting.api.model.TimeGranularity
import ro.jf.funds.reporting.api.model.YearMonth
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.domain.BucketType.FORECAST
import ro.jf.funds.reporting.service.domain.BucketType.REAL
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.service.reportdata.resolver.ReportDataResolverRegistry
import java.math.BigDecimal
import java.util.UUID.randomUUID

class ReportDataServiceTest {
    private val reportViewRepository = mock<ReportViewRepository>()
    private val reportRecordRepository = mock<ReportRecordRepository>()
    private val historicalPricingSdk = mock<HistoricalPricingSdk>()
    private val resolverRegistry = ReportDataResolverRegistry()
    private val reportDataService =
        ReportDataService(reportViewRepository, reportRecordRepository, historicalPricingSdk, resolverRegistry)

    private val userId = randomUUID()
    private val reportViewId = randomUUID()
    private val reportViewName = "view name"
    private val expensesFundId = randomUUID()
    private val allLabels = labelsOf("need", "want")

    @Test
    fun `get net data grouped by months`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            filter = RecordFilter(labels = allLabels),
            groups = null,
            features = ReportDataFeaturesConfiguration()
                .withNet(enabled = true, applyFilter = true)
                .withValueReport(enabled = true),
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration))
        val interval = DateInterval(LocalDate.parse("2021-09-03"), LocalDate.parse("2021-11-25"))
        whenever(reportRecordRepository.findByViewUntil(userId, reportViewId, interval.to))
            .thenReturn(
                listOf(
                    ronReportRecord(LocalDate(2021, 9, 3), -100, labelsOf("need")),
                    eurReportRecord(LocalDate(2021, 9, 15), -40, -200, labelsOf("want")),
                    ronReportRecord(LocalDate(2021, 10, 7), -30, labelsOf("want")),
                    ronReportRecord(LocalDate(2021, 10, 8), -16, labelsOf("other")),
                )
            )
        val granularInterval = GranularDateInterval(interval, TimeGranularity.MONTHLY)
        val conversionsResponse = mock<ConversionsResponse>()
        whenever(conversionsResponse.getRate(eq(EUR), eq(RON), any())).thenReturn(BigDecimal("5.0"))
        whenever(historicalPricingSdk.convert(eq(userId), any())).thenReturn(conversionsResponse)

        val data = reportDataService.getReportViewData(userId, reportViewId, granularInterval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.granularInterval).isEqualTo(granularInterval)
        assertThat(data.data[0].timeBucket)
            .isEqualTo(DateInterval(LocalDate.parse("2021-09-01"), LocalDate.parse("2021-09-30")))
        assertThat(data.data[0].aggregate.net).isEqualByComparingTo(BigDecimal("-300.0"))
        assertThat(data.data[1].timeBucket)
            .isEqualTo(DateInterval(LocalDate.parse("2021-10-01"), LocalDate.parse("2021-10-31")))
        assertThat(data.data[1].aggregate.net).isEqualByComparingTo(BigDecimal("-30.0"))
        assertThat(data.data[2].timeBucket)
            .isEqualTo(DateInterval(LocalDate.parse("2021-11-01"), LocalDate.parse("2021-11-30")))
        assertThat(data.data[2].aggregate.net).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `get net data grouped by months with forecast`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            filter = RecordFilter(labels = allLabels),
            groups = null,
            features = ReportDataFeaturesConfiguration()
                .withNet(enabled = true, applyFilter = true)
                .withForecast(enabled = true, forecastBuckets = 3, consideredBuckets = 5)
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration))
        val interval = DateInterval(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-06-30"))
        whenever(reportRecordRepository.findByViewUntil(userId, reportViewId, interval.to))
            .thenReturn(
                listOf(
                    ronReportRecord(LocalDate(2021, 1, 3), -100, labelsOf("need")),
                    ronReportRecord(LocalDate(2021, 2, 15), -40, labelsOf("want")),
                    ronReportRecord(LocalDate(2021, 3, 7), -30, labelsOf("want")),
                    ronReportRecord(LocalDate(2021, 4, 8), -20, labelsOf("need")),
                    ronReportRecord(LocalDate(2021, 5, 8), -40, labelsOf("need")),
                    ronReportRecord(LocalDate(2021, 6, 8), -50, labelsOf("want")),
                )
            )
        val granularInterval = GranularDateInterval(interval, TimeGranularity.MONTHLY)
        val conversionsResponse = mock<ConversionsResponse>()
        whenever(historicalPricingSdk.convert(eq(userId), any())).thenReturn(conversionsResponse)

        val data = reportDataService.getReportViewData(userId, reportViewId, granularInterval)

        val acceptedOffset = within(BigDecimal("0.01"))

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.granularInterval).isEqualTo(granularInterval)
        assertThat(data.data).hasSize(9)
        assertThat(data.data[5].bucketType).isEqualTo(REAL)
        assertThat(data.data[5].aggregate.net).isCloseTo(BigDecimal(-50), acceptedOffset)
        assertThat(data.data[6].timeBucket).isEqualTo(YearMonth(2021, 7).asDateInterval())
        assertThat(data.data[6].bucketType).isEqualTo(FORECAST)
        assertThat(data.data[6].aggregate.net).isCloseTo(
            BigDecimal((-40 - 30 - 20 - 40 - 50) / 5.0),
            acceptedOffset
        ) // -36
        assertThat(data.data[7].aggregate.net).isCloseTo(
            BigDecimal((-30 - 20 - 40 - 50 - 36) / 5.0),
            acceptedOffset
        ) // -35.2
        assertThat(data.data[8].aggregate.net).isCloseTo(
            BigDecimal((-20 - 40 - 50 - 36 - 35.2) / 5.0),
            acceptedOffset
        ) // -36.24
    }

    @Test
    fun `get grouped net data grouped by months`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            filter = RecordFilter(labels = allLabels),
            groups = listOf(
                ReportGroup("Need", RecordFilter.byLabels("need")),
                ReportGroup("Want", RecordFilter.byLabels("want"))
            ),
            features = ReportDataFeaturesConfiguration()
                .withGroupedNet(enabled = true)
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration))
        val interval = DateInterval(YearMonth(2021, 9), YearMonth(2021, 10))
        whenever(reportRecordRepository.findByViewUntil(userId, reportViewId, interval.to))
            .thenReturn(
                listOf(
                    ronReportRecord(LocalDate.parse("2021-09-03"), -100, labelsOf("need")),
                    eurReportRecord(LocalDate.parse("2021-09-04"), -10, -50, labelsOf("need")),
                    eurReportRecord(LocalDate.parse("2021-09-15"), -40, -200, labelsOf("want")),
                    ronReportRecord(LocalDate.parse("2021-10-18"), -30, labelsOf("want")),
                    ronReportRecord(LocalDate.parse("2021-09-28"), -16, labelsOf("other")),
                )
            )
        val granularInterval = GranularDateInterval(interval, TimeGranularity.MONTHLY)
        val conversionsResponse = mock<ConversionsResponse>()
        whenever(conversionsResponse.getRate(eq(EUR), eq(RON), any())).thenReturn(BigDecimal("5.0"))
        whenever(historicalPricingSdk.convert(eq(userId), any())).thenReturn(conversionsResponse)

        val data = reportDataService.getReportViewData(userId, reportViewId, granularInterval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.granularInterval).isEqualTo(granularInterval)
        assertThat(data.data).hasSize(2)
        assertThat(data.data[0].timeBucket)
            .isEqualTo(DateInterval(YearMonth(2021, 9), YearMonth(2021, 9)))
        assertThat(data.data[0].aggregate.groupedNet?.get("Need")).isEqualByComparingTo(BigDecimal("-150.0"))
        assertThat(data.data[0].aggregate.groupedNet?.get("Want")).isEqualByComparingTo(BigDecimal("-200.0"))
        assertThat(data.data[1].timeBucket)
            .isEqualTo(DateInterval(YearMonth(2021, 10), YearMonth(2021, 10)))
        assertThat(data.data[1].aggregate.groupedNet?.get("Need")).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(data.data[1].aggregate.groupedNet?.get("Want")).isEqualByComparingTo("-30.0")
    }

    @Test
    fun `get grouped budget should distribute external income`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            filter = RecordFilter(labels = allLabels),
            groups = listOf(
                ReportGroup("Need", RecordFilter.byLabels("need")),
                ReportGroup("Want", RecordFilter.byLabels("want"))
            ),
            features = ReportDataFeaturesConfiguration()
                .withGroupedBudget(
                    enabled = true,
                    distributions = listOf(
                        needWantDistribution(true, null, 60, 40),
                    ),
                )
        )
        whenever(reportViewRepository.findById(userId, reportViewId)).thenReturn(reportView(reportDataConfiguration))
        val conversions = mock<ConversionsResponse>()
        whenever(conversions.getRate(eq(EUR), eq(RON), any())).thenReturn(BigDecimal("5.00"))
        whenever(historicalPricingSdk.convert(eq(userId), any())).thenReturn(conversions)
        val interval = DateInterval(YearMonth(2020, 2), YearMonth(2020, 3))
        val granularInterval = GranularDateInterval(interval, TimeGranularity.MONTHLY)
        whenever(reportRecordRepository.findByViewUntil(userId, reportViewId, interval.to))
            .thenReturn(
                listOf(
                    // previous month with specific distribution
                    ronReportRecord(LocalDate(2020, 1, 5), 1000, labelsOf("income")),
                    eurReportRecord(LocalDate(2020, 1, 10), 500, 2500, labelsOf()),
                    // first month
                    ronReportRecord(LocalDate(2020, 2, 5), amount = 1500, labels = labelsOf()),
                    ronReportRecord(LocalDate(2020, 2, 10), amount = -300, labels = labelsOf()),
                    eurReportRecord(LocalDate(2020, 2, 15), 300, 1500, labelsOf()),
                    // second month
                    ronReportRecord(LocalDate(2020, 3, 5), amount = 2000, labels = labelsOf()),
                )
            )

        val data = reportDataService.getReportViewData(userId, reportViewId, granularInterval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.granularInterval).isEqualTo(granularInterval)
        assertThat(data.data).hasSize(2)

        assertThat(data.data[0].timeBucket)
            .isEqualTo(DateInterval(YearMonth(2020, 2), YearMonth(2020, 2)))
        val groupedBudget1 = data.data[0].aggregate.groupedBudget ?: error("First grouped budget is null")
        assertThat(groupedBudget1["Need"]).isNotNull
        groupedBudget1["Need"]?.let {
            assertThat(it.allocated).isEqualByComparingTo(BigDecimal(1500 * 0.6 - 300 * 0.6 + 300 * 0.6 * 5))
            assertThat(it.spent).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(it.left).isEqualByComparingTo(BigDecimal(1500 * 0.6 - 300 * 0.6 + 1000 * 0.6 + 300 * 0.6 * 5 + 500 * 0.6 * 5))
        }
        assertThat(groupedBudget1["Want"]).isNotNull
        groupedBudget1["Want"]?.let {
            assertThat(it.allocated).isEqualByComparingTo(BigDecimal(1500 * 0.4 - 300 * 0.4 + 300 * 0.4 * 5))
            assertThat(it.spent).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(it.left).isEqualByComparingTo(BigDecimal(1500 * 0.4 - 300 * 0.4 + 1000 * 0.4 + 300 * 0.4 * 5 + 500 * 0.4 * 5))
        }

        assertThat(data.data[1].timeBucket)
            .isEqualTo(DateInterval(YearMonth(2020, 3), YearMonth(2020, 3)))
        val groupedBudget2 = data.data[1].aggregate.groupedBudget ?: error("Second grouped budget is null")
        assertThat(groupedBudget2["Need"]).isNotNull
        groupedBudget2["Need"]?.let {
            assertThat(it.allocated).isEqualByComparingTo(BigDecimal(2000 * 0.6))
            assertThat(it.spent).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(it.left).isEqualByComparingTo(BigDecimal(2000 * 0.6 + 1500 * 0.6 - 300 * 0.6 + 1000 * 0.6 + 300 * 0.6 * 5 + 500 * 0.6 * 5))
        }
        assertThat(groupedBudget2["Want"]).isNotNull
        groupedBudget2["Want"]?.let {
            assertThat(it.allocated).isEqualByComparingTo(BigDecimal(2000 * 0.4))
            assertThat(it.spent).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(it.left).isEqualByComparingTo(BigDecimal(2000 * 0.4 + 1500 * 0.4 - 300 * 0.4 + 1000 * 0.4 + 300 * 0.4 * 5 + 500 * 0.4 * 5))
        }
    }

    @Test
    fun `get grouped budget should calculate expenses and distribute currencies`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            filter = RecordFilter(labels = allLabels),
            groups = listOf(
                ReportGroup("Need", RecordFilter.byLabels("need")),
                ReportGroup("Want", RecordFilter.byLabels("want"))
            ),
            features = ReportDataFeaturesConfiguration()
                .withGroupedBudget(
                    enabled = true,
                    distributions = listOf(
                        needWantDistribution(true, null, 60, 40),
                    ),
                )
        )
        whenever(reportViewRepository.findById(userId, reportViewId)).thenReturn(reportView(reportDataConfiguration))
        val conversions = mock<ConversionsResponse>()
        whenever(conversions.getRate(eq(EUR), eq(RON), any())).thenReturn(BigDecimal("5.00"))
        whenever(historicalPricingSdk.convert(eq(userId), any())).thenReturn(conversions)
        val interval = DateInterval(YearMonth(2020, 2), YearMonth(2020, 2))
        val granularInterval = GranularDateInterval(interval, TimeGranularity.MONTHLY)
        whenever(reportRecordRepository.findByViewUntil(userId, reportViewId, interval.to))
            .thenReturn(
                listOf(
                    // previous month with specific distribution
                    ronReportRecord(LocalDate(2020, 1, 5), 1000, labelsOf("income")),
                    // first month
                    ronReportRecord(LocalDate(2020, 2, 5), 2000, labelsOf()),
                    ronReportRecord(LocalDate(2020, 2, 10), -100, labelsOf("need")),
                    eurReportRecord(LocalDate(2020, 2, 15), 500, 2500, labelsOf()),
                    ronReportRecord(LocalDate(2020, 2, 20), -200, labelsOf("need")),
                    ronReportRecord(LocalDate(2020, 2, 21), -500, labelsOf("need")),
                    eurReportRecord(LocalDate(2020, 2, 25), -100, -500, labelsOf("want")),
                )
            )

        val data = reportDataService.getReportViewData(userId, reportViewId, granularInterval)

        assertThat(data.data).hasSize(1)

        val groupedBudget1 = data.data[0].aggregate.groupedBudget ?: error("First grouped budget is null")

        /**
         * Total left = 2200 RON + 400 EUR
         * RON/EUR ratio = 2200 / 400 = 5.5
         * RON/EUR rate = 5
         * Need:
         *      Allocated: 1200 RON + 300 EUR
         *      Spent: 800 RON
         *      Initial Left = 1000 RON + 300 EUR
         *          1000 + 5A = 5.5(300 - A) => -650 = -10.5A => A = 61.905
         *      Real Left = 1309.525 + 238.095 EUR
         * Want:
         *      Allocated: 800 RON + 200 EUR
         *      Spent: 100 EUR
         *      Initial Left = 1200 RON + 100 EUR
         *          1200 + 5A = 5.5(100 - A) => 650 = -10.5A => A = 61.905
         *      Real Left = 890.475 RON + 161.905 EUR
         */
        val acceptedOffset = within(BigDecimal("0.01"))
        assertThat(groupedBudget1["Need"]).isNotNull
        groupedBudget1["Need"]?.let {
            assertThat(it.allocated).isCloseTo(BigDecimal(1200 + 300 * 5), acceptedOffset)
            assertThat(it.spent).isCloseTo(BigDecimal(-800), acceptedOffset)
            assertThat(it.left).isCloseTo(BigDecimal(1309.525 + 238.095 * 5), acceptedOffset)
        }
        assertThat(groupedBudget1["Want"]).isNotNull
        groupedBudget1["Want"]?.let {
            assertThat(it.allocated).isCloseTo(BigDecimal(800 + 200 * 5), acceptedOffset)
            assertThat(it.spent).isCloseTo(BigDecimal(-500), acceptedOffset)
            assertThat(it.left).isCloseTo(BigDecimal(890.475 + 161.905 * 5), acceptedOffset)
        }
    }

    @Test
    fun `get grouped budget should adapt to changing currency exchange rates`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            filter = RecordFilter(labels = allLabels),
            groups = listOf(
                ReportGroup("Need", RecordFilter.byLabels("need")),
                ReportGroup("Want", RecordFilter.byLabels("want"))
            ),
            features = ReportDataFeaturesConfiguration()
                .withGroupedBudget(
                    enabled = true,
                    distributions = listOf(
                        needWantDistribution(true, null, 60, 40),
                    ),
                )
        )
        whenever(reportViewRepository.findById(userId, reportViewId)).thenReturn(reportView(reportDataConfiguration))
        val conversions = mock<ConversionsResponse>()
        whenever(conversions.getRate(eq(EUR), eq(RON), any()))
            .thenAnswer {
                val date = it.getArgument<LocalDate>(2)
                if (date.month in listOf(Month.JANUARY, Month.FEBRUARY)) {
                    BigDecimal("4.8")
                } else {
                    BigDecimal("4.9")
                }
            }
        whenever(historicalPricingSdk.convert(eq(userId), any())).thenReturn(conversions)
        val interval = DateInterval(YearMonth(2020, 2), YearMonth(2020, 3))
        val granularInterval = GranularDateInterval(interval, TimeGranularity.MONTHLY)
        whenever(reportRecordRepository.findByViewUntil(userId, reportViewId, interval.to))
            .thenReturn(
                listOf(
                    // first month
                    ronReportRecord(LocalDate(2020, 2, 5), 2000, labelsOf()),
                    eurReportRecord(LocalDate(2020, 2, 15), 500, 2500, labelsOf()),
                    ronReportRecord(LocalDate(2020, 2, 10), -800, labelsOf("need")),
                    eurReportRecord(LocalDate(2020, 2, 25), -100, -500, labelsOf("want")),
                    // second month
                    ronReportRecord(LocalDate(2020, 3, 5), 2500, labelsOf()),
                    eurReportRecord(LocalDate(2020, 3, 15), 400, 2000, labelsOf()),
                    ronReportRecord(LocalDate(2020, 3, 20), -300, labelsOf("want")),
                    eurReportRecord(LocalDate(2020, 3, 25), -200, -1000, labelsOf("need")),
                )
            )

        val data = reportDataService.getReportViewData(userId, reportViewId, granularInterval)

        assertThat(data.data).hasSize(2)

        val acceptedOffset = within(BigDecimal("0.1"))

        /**
         * 2020 February
         * Total left = 1200 RON + 400 EUR
         * RON/EUR rate = 4.8
         * Need:
         *      Allocated: 1200 RON + 300 EUR
         *      Spent: 800 RON
         *      Initial Left = 400 RON + 300 EUR
         *          400 + 4.8 * 300 = X (1200 + 400 * 4.8) => X = 1840 / 3120 = 0,58974
         *      Real Left = 707,688 RON + 235.896 EUR
         * Want:
         *      Allocated: 800 RON + 200 EUR
         *      Spent: 100 EUR
         *      Initial Left = 800 RON + 100 EUR
         *          800 + 4.8 * 100 = X (1200 + 400 * 4.8) => X = 1280 / 3120 = 0,41026
         *      Real Left = 492.3120 RON + 164.104 EUR
         */

        val groupedBudget1 = data.data[0].aggregate.groupedBudget ?: error("First grouped budget is null")
        assertThat(groupedBudget1["Need"]).isNotNull
        groupedBudget1["Need"]?.let {
            assertThat(it.allocated).isCloseTo(BigDecimal(1200 + 300 * 4.8), acceptedOffset)
            assertThat(it.spent).isCloseTo(BigDecimal(-800), acceptedOffset)
            assertThat(it.left).isCloseTo(BigDecimal(707.688 + 235.896 * 4.8), acceptedOffset)
        }
        assertThat(groupedBudget1["Want"]).isNotNull
        groupedBudget1["Want"]?.let {
            assertThat(it.allocated).isCloseTo(BigDecimal(800 + 200 * 4.8), acceptedOffset)
            assertThat(it.spent).isCloseTo(BigDecimal(4.8 * -100), acceptedOffset)
            assertThat(it.left).isCloseTo(BigDecimal(492.312 + 164.104 * 4.8), acceptedOffset)
        }

        /**
         * March 2020
         * Total left = 3400 RON + 600 EUR
         * RON/EUR rate = 4.9
         * Need
         *      Allocated: 1500 RON + 240 EUR
         *      Spent: -200 EUR
         *      Initial Left = 707,688 RON + 235.896 EUR + 1500 RON + 40 EUR = 2207,688 RON + 275.896 EUR
         *          2207,688 RON + 275.896 EUR * 4.9 = X (3400 + 600 * 4.9) => X = 3.559,5784 / 6340 = 0,5614476972
         *      Real Left = 1.908,92217 RON + 336,86862 EUR
         *  Want
         *      Allocated: 1000 RON + 160 EUR
         *      Spent: -300 RON
         *      Initial Left = 492.3120 RON + 164.104 EUR + 700 RON + 160 EUR = 1192.312 RON + 324.104 EUR
         *          1192.312 RON + 324.104 EUR * 4.9 = X (3400 + 600 * 4.9) => X = 2.780,4216 / 6340 = 0,4385523028
         *      Real Left = 1.491,0778 RON + 263,13138 EUR
         */
        val groupedBudget2 = data.data[1].aggregate.groupedBudget ?: error("First grouped budget is null")
        assertThat(groupedBudget2["Need"]).isNotNull
        groupedBudget2["Need"]?.let {
            assertThat(it.allocated).isCloseTo(BigDecimal(1500 + 240 * 4.9), acceptedOffset)
            assertThat(it.spent).isCloseTo(BigDecimal(-200 * 4.9), acceptedOffset)
            assertThat(it.left).isCloseTo(BigDecimal(1908.92217 + 336.86862 * 4.9), acceptedOffset)
        }
        assertThat(groupedBudget2["Want"]).isNotNull
        groupedBudget2["Want"]?.let {
            assertThat(it.allocated).isCloseTo(BigDecimal(1000 + 160 * 4.9), acceptedOffset)
            assertThat(it.spent).isCloseTo(BigDecimal(-300), acceptedOffset)
            assertThat(it.left).isCloseTo(BigDecimal(1491.0778 + 263.13138 * 4.9), acceptedOffset)
        }
    }

    @Test
    fun `get grouped budget should adapt to budget distribution change`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            filter = RecordFilter(labels = allLabels),
            groups = listOf(
                ReportGroup("Need", RecordFilter.byLabels("need")),
                ReportGroup("Want", RecordFilter.byLabels("want"))
            ),
            features = ReportDataFeaturesConfiguration()
                .withGroupedBudget(
                    enabled = true,
                    distributions = listOf(
                        needWantDistribution(true, null, 60, 40),
                        needWantDistribution(false, YearMonth(2020, 3), 70, 30),
                    ),
                )
        )
        whenever(reportViewRepository.findById(userId, reportViewId)).thenReturn(reportView(reportDataConfiguration))
        val conversions = mock<ConversionsResponse>()
        whenever(conversions.getRate(eq(EUR), eq(RON), any())).thenReturn(BigDecimal("5.00"))
        whenever(historicalPricingSdk.convert(eq(userId), any())).thenReturn(conversions)
        val interval = DateInterval(YearMonth(2020, 2), YearMonth(2020, 3))
        val granularInterval = GranularDateInterval(interval, TimeGranularity.MONTHLY)
        whenever(reportRecordRepository.findByViewUntil(userId, reportViewId, interval.to))
            .thenReturn(
                listOf(
                    // first month
                    ronReportRecord(LocalDate(2020, 2, 5), 2000, labelsOf()),
                    eurReportRecord(LocalDate(2020, 2, 15), 500, 2500, labelsOf()),
                    ronReportRecord(LocalDate(2020, 2, 10), -800, labelsOf("need")),
                    eurReportRecord(LocalDate(2020, 2, 25), -100, -500, labelsOf("want")),
                    // second month
                    ronReportRecord(LocalDate(2020, 3, 5), 2500, labelsOf()),
                    eurReportRecord(LocalDate(2020, 3, 15), 400, 2000, labelsOf()),
                    ronReportRecord(LocalDate(2020, 3, 20), -300, labelsOf("want")),
                    eurReportRecord(LocalDate(2020, 3, 25), -200, -1000, labelsOf("need")),
                )
            )

        val data = reportDataService.getReportViewData(userId, reportViewId, granularInterval)

        assertThat(data.data).hasSize(2)

        val acceptedOffset = within(BigDecimal("0.01"))

        /**
         * 2020 February
         * Total left = 1200 RON + 400 EUR
         * RON/EUR rate = 5
         * Distribution: 60% Need, 40% Want
         * Need:
         *      Allocated: 1200 RON + 300 EUR
         *      Spent: 800 RON
         *      Initial Left = 400 RON + 300 EUR
         *          400 + 5 * 300 = X (1200 + 400 * 5) => X = 1900 / 3200 = 0,59375
         *      Real Left = 712.5 RON + 237,5 EUR
         * Want:
         *      Allocated: 800 RON + 200 EUR
         *      Spent: 100 EUR
         *      Initial Left = 800 RON + 100 EUR
         *          800 + 5 * 100 = X (1200 + 400 * 5) => X = 1300 / 3200 = 0,40625 = 0,41026
         *      Real Left = 487.5 RON + 162.5 EUR
         */

        val groupedBudget1 = data.data[0].aggregate.groupedBudget ?: error("First grouped budget is null")
        assertThat(groupedBudget1["Need"]).isNotNull
        groupedBudget1["Need"]?.let {
            assertThat(it.allocated).isCloseTo(BigDecimal(1200 + 300 * 5), acceptedOffset)
            assertThat(it.spent).isCloseTo(BigDecimal(-800), acceptedOffset)
            assertThat(it.left).isCloseTo(BigDecimal(712.5 + 237.5 * 5), acceptedOffset)
        }
        assertThat(groupedBudget1["Want"]).isNotNull
        groupedBudget1["Want"]?.let {
            assertThat(it.allocated).isCloseTo(BigDecimal(800 + 200 * 5), acceptedOffset)
            assertThat(it.spent).isCloseTo(BigDecimal(-100 * 5), acceptedOffset)
            assertThat(it.left).isCloseTo(BigDecimal(487.5 + 162.5 * 5), acceptedOffset)
        }

        /**
         * March 2020
         * Total left = 3400 RON + 600 EUR
         * RON/EUR rate = 5
         * Distribution: 70% Need, 30% Want
         * Need
         *      Allocated: 1750 RON + 280 EUR
         *      Spent: -200 EUR
         *      Initial Left = 712.5 RON + 237,5 EUR + 1750 RON + 80 EUR = 2462.5 RON + 317.5 EUR
         *          2462.5 RON + 317.5 EUR * 5 = X (3400 + 600 * 5) => X = 4.050 / 6400 = 0,6328125
         *      Real Left = 2151.5625 RON + 379.6875 EUR
         *  Want
         *      Allocated: 750 RON + 120 EUR
         *      Spent: -300 RON
         *      Initial Left = 487.5 RON + 162.5 EUR + 450 RON + 120 EUR = 937.5 RON + 282.5 EUR
         *          937.5 RON + 282.5 EUR * 5 = X (3400 + 600 * 59) => X = 2350 / 6400 = 0,3671875
         *      Real Left = 1248.4375 RON + 220.3125 EUR
         */
        val groupedBudget2 = data.data[1].aggregate.groupedBudget ?: error("First grouped budget is null")
        assertThat(groupedBudget2["Need"]).isNotNull
        groupedBudget2["Need"]?.let {
            assertThat(it.allocated).isCloseTo(BigDecimal(1750 + 280 * 5), acceptedOffset)
            assertThat(it.spent).isCloseTo(BigDecimal(-200 * 5), acceptedOffset)
            assertThat(it.left).isCloseTo(BigDecimal(2151.5625 + 379.6875 * 5), acceptedOffset)
        }
        assertThat(groupedBudget2["Want"]).isNotNull
        groupedBudget2["Want"]?.let {
            assertThat(it.allocated).isCloseTo(BigDecimal(750 + 120 * 5), acceptedOffset)
            assertThat(it.spent).isCloseTo(BigDecimal(-300), acceptedOffset)
            assertThat(it.left).isCloseTo(BigDecimal(1248.4375 + 220.3125 * 5), acceptedOffset)
        }
    }

    @Test
    fun `get monthly value data with single currency`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            filter = RecordFilter(labels = allLabels),
            groups = null,
            features = ReportDataFeaturesConfiguration()
                .withNet(enabled = true, applyFilter = true)
                .withValueReport(enabled = true),
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration))
        whenever(historicalPricingSdk.convert(eq(userId), eq(ConversionsRequest(emptyList()))))
            .thenReturn(ConversionsResponse(emptyList()))
        val to = LocalDate.parse("2021-11-25")
        val interval = DateInterval(from = LocalDate.parse("2021-09-02"), to = to)
        whenever(reportRecordRepository.findByViewUntil(userId, reportViewId, to))
            .thenReturn(
                listOf(
                    ronReportRecord(LocalDate.parse("2021-08-02"), 100, labelsOf("need")),
                    ronReportRecord(LocalDate.parse("2021-09-02"), 200, labelsOf("need")),
                    ronReportRecord(LocalDate.parse("2021-09-03"), -100, labelsOf("need")),
                    ronReportRecord(LocalDate.parse("2021-09-15"), -40, labelsOf("want")),
                    ronReportRecord(LocalDate.parse("2021-10-07"), 400, labelsOf("want")),
                    ronReportRecord(LocalDate.parse("2021-10-07"), -30, labelsOf("want")),
                    ronReportRecord(LocalDate.parse("2021-10-08"), -16, labelsOf("other")),
                )
            )
        val granularInterval = GranularDateInterval(interval, TimeGranularity.MONTHLY)

        val data = reportDataService.getReportViewData(userId, reportViewId, granularInterval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.granularInterval).isEqualTo(granularInterval)
        assertThat(data.data[0].aggregate.value?.start)
            .isEqualByComparingTo(BigDecimal("100.0"))
        assertThat(data.data[0].aggregate.value?.end)
            .isEqualByComparingTo(BigDecimal("160.0"))
        assertThat(data.data[1].aggregate.value?.start)
            .isEqualByComparingTo(BigDecimal("160.0"))
        assertThat(data.data[1].aggregate.value?.end)
            .isEqualByComparingTo(BigDecimal("514.0"))
        assertThat(data.data[2].aggregate.value?.start)
            .isEqualByComparingTo(BigDecimal("514.0"))
        assertThat(data.data[2].aggregate.value?.end)
            .isEqualByComparingTo(BigDecimal("514.0"))
    }

    // TODO(Johann) add test for value data

    @Test
    fun `get monthly value data with multiple currencies`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            filter = RecordFilter(labels = allLabels),
            groups = null,
            features = ReportDataFeaturesConfiguration()
                .withNet(enabled = true, applyFilter = true)
                .withValueReport(enabled = true),
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration))
        val to = LocalDate.parse("2021-10-30")
        val interval = DateInterval(from = LocalDate.parse("2021-09-02"), to = to)
        whenever(reportRecordRepository.findByViewUntil(userId, reportViewId, to))
            .thenReturn(
                listOf(
                    ronReportRecord(LocalDate.parse("2021-08-02"), 100, labelsOf("need")),
                    eurReportRecord(LocalDate.parse("2021-08-05"), 20, 98, labelsOf("need")),
                    ronReportRecord(LocalDate.parse("2021-09-02"), 100, labelsOf("need")),
                    eurReportRecord(LocalDate.parse("2021-09-03"), 20, 99, labelsOf("need")),
                )
            )
        val conversionRequest = ConversionsRequest(
            listOf(
                ConversionRequest(EUR, RON, LocalDate(2021, 9, 1)),
                ConversionRequest(EUR, RON, LocalDate(2021, 9, 30)),
                ConversionRequest(EUR, RON, LocalDate(2021, 10, 1)),
                ConversionRequest(EUR, RON, LocalDate(2021, 10, 31)),
            )
        )
        val conversionsResponse = ConversionsResponse(
            listOf(
                ConversionResponse(EUR, RON, LocalDate(2021, 9, 1), BigDecimal("4.85")),
                ConversionResponse(EUR, RON, LocalDate(2021, 9, 30), BigDecimal("4.9")),
                ConversionResponse(EUR, RON, LocalDate(2021, 10, 1), BigDecimal("4.95")),
                ConversionResponse(EUR, RON, LocalDate(2021, 10, 31), BigDecimal("5.0")),
            )
        )
        whenever(historicalPricingSdk.convert(eq(userId), any())).thenReturn(conversionsResponse)
        val granularInterval = GranularDateInterval(interval, TimeGranularity.MONTHLY)

        val data = reportDataService.getReportViewData(userId, reportViewId, granularInterval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.granularInterval).isEqualTo(granularInterval)
        assertThat(data.data[0].timeBucket)
            .isEqualTo(DateInterval(LocalDate(2021, 9, 1), LocalDate(2021, 9, 30)))
        assertThat(data.data[0].aggregate.value?.start).isEqualByComparingTo(BigDecimal("197.0"))
        assertThat(data.data[0].aggregate.value?.end).isEqualByComparingTo(
            BigDecimal("200.0") + BigDecimal("4.9") * BigDecimal(
                "40.0"
            )
        )
        assertThat(data.data[1].timeBucket)
            .isEqualTo(DateInterval(LocalDate(2021, 10, 1), LocalDate(2021, 10, 31)))
        assertThat(data.data[1].aggregate.value?.start).isEqualByComparingTo(
            BigDecimal("200.0") + BigDecimal("4.95") * BigDecimal(
                "40.0"
            )
        )
        assertThat(data.data[1].aggregate.value?.end).isEqualByComparingTo(
            BigDecimal("200.0") + BigDecimal("5.0") * BigDecimal(
                "40.0"
            )
        )

        val conversionsRequestCaptor = argumentCaptor<ConversionsRequest>()
        verify(historicalPricingSdk).convert(eq(userId), conversionsRequestCaptor.capture())
        assertThat(conversionsRequestCaptor.firstValue.conversions).containsOnlyOnceElementsOf(
            conversionRequest.conversions
        )
    }

    private fun reportView(
        dataConfiguration: ReportDataConfiguration,
    ) = ReportView(reportViewId, userId, reportViewName, expensesFundId, dataConfiguration)

    private fun ronReportRecord(
        date: LocalDate, amount: Int, labels: List<Label>,
    ) = reportRecord(date, RON, BigDecimal(amount), BigDecimal(amount), labels)

    private fun eurReportRecord(
        date: LocalDate, amount: Int, reportCurrencyAmount: Int, labels: List<Label>,
    ) = reportRecord(date, EUR, BigDecimal(amount), BigDecimal(reportCurrencyAmount), labels)

    private fun reportRecord(
        date: LocalDate, unit: FinancialUnit, amount: BigDecimal, reportCurrencyAmount: BigDecimal, labels: List<Label>,
    ) =
        ReportRecord(randomUUID(), userId, randomUUID(), reportViewId, date, unit, amount, reportCurrencyAmount, labels)

    private fun needWantDistribution(
        default: Boolean, from: YearMonth?, needPercentage: Int, wantPercentage: Int,
    ) = GroupedBudgetReportFeature.BudgetDistribution(
        default, from, listOf(
            GroupedBudgetReportFeature.GroupBudgetPercentage("Need", needPercentage),
            GroupedBudgetReportFeature.GroupBudgetPercentage("Want", wantPercentage),
        )
    )
}
