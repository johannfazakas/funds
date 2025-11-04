package ro.jf.funds.reporting.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.atTime
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.commons.model.*
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Currency.Companion.EUR
import ro.jf.funds.commons.model.Currency.Companion.RON
import ro.jf.funds.fund.api.model.TransactionFilterTO
import ro.jf.funds.fund.api.model.TransactionRecordTO
import ro.jf.funds.fund.api.model.TransactionTO
import ro.jf.funds.fund.sdk.TransactionSdk
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.service.reportdata.ConversionRateService
import ro.jf.funds.reporting.service.service.reportdata.InterestRateCalculator
import ro.jf.funds.reporting.service.service.reportdata.ReportDataService
import ro.jf.funds.reporting.service.service.reportdata.ReportTransactionService
import ro.jf.funds.reporting.service.service.reportdata.forecast.AverageForecastStrategy
import ro.jf.funds.reporting.service.service.reportdata.resolver.*
import java.math.BigDecimal
import java.util.*
import java.util.UUID.randomUUID

class ReportDataServiceTest {
    private val reportViewRepository = mock<ReportViewRepository>()
    private val conversionRateService = mock<ConversionRateService>()
    private val fundTransactionSdk = mock<TransactionSdk>()
    private val reportTransactionService = ReportTransactionService(fundTransactionSdk)
    private val forecastStrategy = AverageForecastStrategy()

    private val resolverRegistry = ReportDataResolverRegistry(
        NetDataResolver(conversionRateService, forecastStrategy),
        ValueReportDataResolver(conversionRateService, forecastStrategy),
        GroupedNetDataResolver(conversionRateService, forecastStrategy),
        GroupedBudgetDataResolver(conversionRateService, forecastStrategy),
        PerformanceReportDataResolver(conversionRateService, forecastStrategy),
        InstrumentPerformanceReportDataResolver(conversionRateService, forecastStrategy),
        InterestRateReportResolver(conversionRateService, InterestRateCalculator(), forecastStrategy),
        InstrumentInterestRateReportResolver(conversionRateService, InterestRateCalculator(), forecastStrategy),
    )
    private val reportDataService =
        ReportDataService(reportViewRepository, resolverRegistry, reportTransactionService)

    private val userId = randomUUID()
    private val reportViewId = randomUUID()
    private val reportViewName = "view name"
    private val expensesFundId = randomUUID()
    private val investmentFundId = randomUUID()
    private val allLabels = labelsOf("need", "want")

    @Test
    fun `get net data grouped by months`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            groups = null,
            reports = ReportsConfiguration()
                .withNet(enabled = true, filter = RecordFilter(labels = allLabels))
                .withValueReport(enabled = true, filter = RecordFilter(labels = allLabels)),
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration, expensesFundId))
        val interval = ReportDataInterval.Monthly(YearMonth(2021, 9), YearMonth(2021, 11))
        mockTransactions(
            interval, expensesFundId, listOf(
                ronTransaction(LocalDate(2021, 9, 3), -100, labelsOf("need")),
                eurTransaction(LocalDate(2021, 9, 15), -40, labelsOf("want")),
                ronTransaction(LocalDate(2021, 10, 7), -30, labelsOf("want")),
                ronTransaction(LocalDate(2021, 10, 8), -16, labelsOf("other")),
            )
        )

        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON))).thenReturn(BigDecimal.ONE)
        whenever(conversionRateService.getRate(any(), eq(EUR), eq(RON))).thenReturn(BigDecimal("5.0"))

        val data = reportDataService.getNetReport(userId, reportViewId, interval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.interval).isEqualTo(interval)
        assertThat(data.buckets[0].timeBucket)
            .isEqualTo(TimeBucket(LocalDate.parse("2021-09-01"), LocalDate.parse("2021-09-30")))
        assertThat(data.buckets[0].report.net).isEqualByComparingTo(BigDecimal("-300.0"))
        assertThat(data.buckets[1].timeBucket)
            .isEqualTo(TimeBucket(LocalDate.parse("2021-10-01"), LocalDate.parse("2021-10-31")))
        assertThat(data.buckets[1].report.net).isEqualByComparingTo(BigDecimal("-30.0"))
        assertThat(data.buckets[2].timeBucket)
            .isEqualTo(TimeBucket(LocalDate.parse("2021-11-01"), LocalDate.parse("2021-11-30")))
        assertThat(data.buckets[2].report.net).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `get net data grouped by months with forecast`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            groups = null,
            reports = ReportsConfiguration()
                .withNet(enabled = true, filter = RecordFilter(labels = allLabels)),
            forecast = ForecastConfiguration(5)
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration, expensesFundId))
        val interval = ReportDataInterval.Monthly(
            YearMonth(2021, 1),
            YearMonth(2021, 6),
            YearMonth(2021, 9)
        )
        mockTransactions(
            interval, expensesFundId, listOf(
                ronTransaction(LocalDate(2021, 1, 3), -100, labelsOf("need")),
                ronTransaction(LocalDate(2021, 2, 15), -40, labelsOf("want")),
                ronTransaction(LocalDate(2021, 3, 7), -30, labelsOf("want")),
                ronTransaction(LocalDate(2021, 4, 8), -20, labelsOf("need")),
                ronTransaction(LocalDate(2021, 5, 8), -40, labelsOf("need")),
                ronTransaction(LocalDate(2021, 6, 8), -50, labelsOf("want")),
            )
        )
        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON))).thenReturn(BigDecimal.ONE)

        val data = reportDataService.getNetReport(userId, reportViewId, interval)

        val acceptedOffset = within(BigDecimal("0.01"))

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.interval).isEqualTo(interval)
        assertThat(data.buckets).hasSize(9)
        assertThat(data.buckets[5].bucketType).isEqualTo(BucketType.REAL)
        assertThat(data.buckets[5].report.net).isCloseTo(BigDecimal(-50), acceptedOffset)
        assertThat(data.buckets[6].timeBucket).isEqualTo(YearMonth(2021, 7).asTimeBucket())
        assertThat(data.buckets[6].bucketType).isEqualTo(BucketType.FORECAST)
        assertThat(data.buckets[6].report.net).isCloseTo(
            BigDecimal((-40 - 30 - 20 - 40 - 50) / 5.0),
            acceptedOffset
        ) // -36
        assertThat(data.buckets[7].report.net).isCloseTo(
            BigDecimal((-30 - 20 - 40 - 50 - 36) / 5.0),
            acceptedOffset
        ) // -35.2
        assertThat(data.buckets[8].report.net).isCloseTo(
            BigDecimal((-20 - 40 - 50 - 36 - 35.2) / 5.0),
            acceptedOffset
        ) // -36.24
    }

    @Test
    fun `get grouped net data grouped by months`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            groups = listOf(
                ReportGroup("Need", RecordFilter.Companion.byLabels("need")),
                ReportGroup("Want", RecordFilter.Companion.byLabels("want"))
            ),
            reports = ReportsConfiguration()
                .withGroupedNet(enabled = true)
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration, expensesFundId))
        val interval = ReportDataInterval.Monthly(YearMonth(2021, 9), YearMonth(2021, 10))
        mockTransactions(
            interval, expensesFundId, listOf(
                ronTransaction(LocalDate.parse("2021-09-03"), -100, labelsOf("need")),
                eurTransaction(LocalDate.parse("2021-09-04"), -10, labelsOf("need")),
                eurTransaction(LocalDate.parse("2021-09-15"), -40, labelsOf("want")),
                ronTransaction(LocalDate.parse("2021-10-18"), -30, labelsOf("want")),
                ronTransaction(LocalDate.parse("2021-09-28"), -16, labelsOf("other")),
            )
        )
        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON))).thenReturn(BigDecimal.ONE)
        whenever(conversionRateService.getRate(any(), eq(EUR), eq(RON))).thenReturn(BigDecimal("5.0"))

        val data = reportDataService.getGroupedNetReport(userId, reportViewId, interval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.interval).isEqualTo(interval)
        assertThat(data.buckets).hasSize(2)
        assertThat(data.buckets[0].timeBucket)
            .isEqualTo(YearMonth(2021, 9).asTimeBucket())
        assertThat(data.buckets[0].report["Need"]?.net).isEqualByComparingTo(BigDecimal("-150.0"))
        assertThat(data.buckets[0].report["Want"]?.net).isEqualByComparingTo(BigDecimal("-200.0"))
        assertThat(data.buckets[1].timeBucket)
            .isEqualTo(YearMonth(2021, 10).asTimeBucket())
        assertThat(data.buckets[1].report["Need"]?.net).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(data.buckets[1].report["Want"]?.net).isEqualByComparingTo("-30.0")
    }

    @Test
    fun `get grouped budget should distribute external income`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            groups = listOf(
                ReportGroup("Need", RecordFilter.Companion.byLabels("need")),
                ReportGroup("Want", RecordFilter.Companion.byLabels("want"))
            ),
            reports = ReportsConfiguration()
                .withGroupedBudget(
                    enabled = true,
                    distributions = listOf(
                        needWantDistribution(true, null, 60, 40),
                    ),
                )
        )
        whenever(reportViewRepository.findById(userId, reportViewId)).thenReturn(
            reportView(
                reportDataConfiguration,
                expensesFundId
            )
        )
        whenever(conversionRateService.getRate(any(), eq(EUR), eq(RON))).thenReturn(BigDecimal("5.00"))
        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON))).thenReturn(BigDecimal.ONE)
        val interval = ReportDataInterval.Monthly(YearMonth(2020, 2), YearMonth(2020, 3))
        mockTransactions(
            interval, expensesFundId, listOf(
                // previous month with specific distribution
                ronTransaction(LocalDate(2020, 1, 5), 1000, labelsOf("income")),
                eurTransaction(LocalDate(2020, 1, 10), 500, labelsOf()),
                // first month
                ronTransaction(LocalDate(2020, 2, 5), amount = 1500, labels = labelsOf()),
                ronTransaction(LocalDate(2020, 2, 10), amount = -300, labels = labelsOf()),
                eurTransaction(LocalDate(2020, 2, 15), 300, labelsOf()),
                // second month
                ronTransaction(LocalDate(2020, 3, 5), amount = 2000, labels = labelsOf()),
            )
        )

        val data = reportDataService.getGroupedBudgetReport(userId, reportViewId, interval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.interval).isEqualTo(interval)
        assertThat(data.buckets).hasSize(2)

        assertThat(data.buckets[0].timeBucket)
            .isEqualTo(YearMonth(2020, 2).asTimeBucket())
        val groupedBudget1 = data.buckets[0].report
        assertThat(groupedBudget1["Need"]).isNotNull
        groupedBudget1["Need"]?.let {
            assertThat(it.allocated).isEqualByComparingTo(BigDecimal(1500 * 0.6 - 300 * 0.6 + 300 * 0.6 * 5))
            assertThat(it.spent).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(it.left)
                .isEqualByComparingTo(BigDecimal(1500 * 0.6 - 300 * 0.6 + 1000 * 0.6 + 300 * 0.6 * 5 + 500 * 0.6 * 5))
        }
        assertThat(groupedBudget1["Want"]).isNotNull
        groupedBudget1["Want"]?.let {
            assertThat(it.allocated).isEqualByComparingTo(BigDecimal(1500 * 0.4 - 300 * 0.4 + 300 * 0.4 * 5))
            assertThat(it.spent).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(it.left)
                .isEqualByComparingTo(BigDecimal(1500 * 0.4 - 300 * 0.4 + 1000 * 0.4 + 300 * 0.4 * 5 + 500 * 0.4 * 5))
        }

        assertThat(data.buckets[1].timeBucket)
            .isEqualTo(YearMonth(2020, 3).asTimeBucket())
        val groupedBudget2 = data.buckets[1].report
        assertThat(groupedBudget2["Need"]).isNotNull
        groupedBudget2["Need"]?.let {
            assertThat(it.allocated).isEqualByComparingTo(BigDecimal(2000 * 0.6))
            assertThat(it.spent).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(it.left)
                .isEqualByComparingTo(BigDecimal(2000 * 0.6 + 1500 * 0.6 - 300 * 0.6 + 1000 * 0.6 + 300 * 0.6 * 5 + 500 * 0.6 * 5))
        }
        assertThat(groupedBudget2["Want"]).isNotNull
        groupedBudget2["Want"]?.let {
            assertThat(it.allocated).isEqualByComparingTo(BigDecimal(2000 * 0.4))
            assertThat(it.spent).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(it.left)
                .isEqualByComparingTo(BigDecimal(2000 * 0.4 + 1500 * 0.4 - 300 * 0.4 + 1000 * 0.4 + 300 * 0.4 * 5 + 500 * 0.4 * 5))
        }
    }

    @Test
    fun `get grouped budget should calculate expenses and distribute currencies`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            groups = listOf(
                ReportGroup("Need", RecordFilter.Companion.byLabels("need")),
                ReportGroup("Want", RecordFilter.Companion.byLabels("want"))
            ),
            reports = ReportsConfiguration()
                .withGroupedBudget(
                    enabled = true,
                    distributions = listOf(
                        needWantDistribution(true, null, 60, 40),
                    ),
                )
        )
        whenever(reportViewRepository.findById(userId, reportViewId)).thenReturn(
            reportView(
                reportDataConfiguration,
                expensesFundId
            )
        )
        whenever(conversionRateService.getRate(any(), eq(EUR), eq(RON))).thenReturn(BigDecimal("5.0"))
        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON))).thenReturn(BigDecimal.ONE)
        val interval = ReportDataInterval.Monthly(YearMonth(2020, 2), YearMonth(2020, 2))
        mockTransactions(
            interval, expensesFundId, listOf(
                // previous month with specific distribution
                ronTransaction(LocalDate(2020, 1, 5), 1000, labelsOf("income")),
                // first month
                ronTransaction(LocalDate(2020, 2, 5), 2000, labelsOf()),
                ronTransaction(LocalDate(2020, 2, 10), -100, labelsOf("need")),
                eurTransaction(LocalDate(2020, 2, 15), 500, labelsOf()),
                ronTransaction(LocalDate(2020, 2, 20), -200, labelsOf("need")),
                ronTransaction(LocalDate(2020, 2, 21), -500, labelsOf("need")),
                eurTransaction(LocalDate(2020, 2, 25), -100, labelsOf("want")),
            )
        )

        val data = reportDataService.getGroupedBudgetReport(userId, reportViewId, interval)

        assertThat(data.buckets).hasSize(1)

        val groupedBudget1 = data.buckets[0].report

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
            groups = listOf(
                ReportGroup("Need", RecordFilter.Companion.byLabels("need")),
                ReportGroup("Want", RecordFilter.Companion.byLabels("want"))
            ),
            reports = ReportsConfiguration()
                .withGroupedBudget(
                    enabled = true,
                    distributions = listOf(
                        needWantDistribution(true, null, 60, 40),
                    ),
                )
        )
        whenever(reportViewRepository.findById(userId, reportViewId)).thenReturn(
            reportView(
                reportDataConfiguration,
                expensesFundId
            )
        )
        whenever(conversionRateService.getRate(any(), eq(EUR), eq(RON))).thenAnswer {
            val date = it.getArgument<LocalDate>(0)
            if (date.month in listOf(Month.JANUARY, Month.FEBRUARY)) {
                BigDecimal("4.8")
            } else {
                BigDecimal("4.9")
            }
        }
        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON)))
            .thenReturn(BigDecimal.ONE)
        val interval = ReportDataInterval.Monthly(YearMonth(2020, 2), YearMonth(2020, 3))
        mockTransactions(
            interval, expensesFundId, listOf(
                // first month
                ronTransaction(LocalDate(2020, 2, 5), 2000, labelsOf()),
                eurTransaction(LocalDate(2020, 2, 15), 500, labelsOf()),
                ronTransaction(LocalDate(2020, 2, 10), -800, labelsOf("need")),
                eurTransaction(LocalDate(2020, 2, 25), -100, labelsOf("want")),
                // second month
                ronTransaction(LocalDate(2020, 3, 5), 2500, labelsOf()),
                eurTransaction(LocalDate(2020, 3, 15), 400, labelsOf()),
                ronTransaction(LocalDate(2020, 3, 20), -300, labelsOf("want")),
                eurTransaction(LocalDate(2020, 3, 25), -200, labelsOf("need")),
            )
        )

        val data = reportDataService.getGroupedBudgetReport(userId, reportViewId, interval)

        assertThat(data.buckets).hasSize(2)

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

        val groupedBudget1 = data.buckets[0].report
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
        val groupedBudget2 = data.buckets[1].report
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
            groups = listOf(
                ReportGroup("Need", RecordFilter.Companion.byLabels("need")),
                ReportGroup("Want", RecordFilter.Companion.byLabels("want"))
            ),
            reports = ReportsConfiguration()
                .withGroupedBudget(
                    enabled = true,
                    distributions = listOf(
                        needWantDistribution(true, null, 60, 40),
                        needWantDistribution(false, YearMonth(2020, 3), 70, 30),
                    ),
                )
        )
        whenever(reportViewRepository.findById(userId, reportViewId)).thenReturn(
            reportView(
                reportDataConfiguration,
                expensesFundId
            )
        )
        whenever(conversionRateService.getRate(any(), eq(EUR), eq(RON))).thenReturn(BigDecimal("5.0"))
        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON))).thenReturn(BigDecimal.ONE)
        val interval = ReportDataInterval.Monthly(YearMonth(2020, 2), YearMonth(2020, 3))
        mockTransactions(
            interval, expensesFundId, listOf(
                // first month
                ronTransaction(LocalDate(2020, 2, 5), 2000, labelsOf()),
                eurTransaction(LocalDate(2020, 2, 15), 500, labelsOf()),
                ronTransaction(LocalDate(2020, 2, 10), -800, labelsOf("need")),
                eurTransaction(LocalDate(2020, 2, 25), -100, labelsOf("want")),
                // second month
                ronTransaction(LocalDate(2020, 3, 5), 2500, labelsOf()),
                eurTransaction(LocalDate(2020, 3, 15), 400, labelsOf()),
                ronTransaction(LocalDate(2020, 3, 20), -300, labelsOf("want")),
                eurTransaction(LocalDate(2020, 3, 25), -200, labelsOf("need")),
            )
        )

        val data = reportDataService.getGroupedBudgetReport(userId, reportViewId, interval)

        assertThat(data.buckets).hasSize(2)

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

        val groupedBudget1 = data.buckets[0].report
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
        val groupedBudget2 = data.buckets[1].report
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
    fun `get grouped budget with forecast should include future estimated values`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            groups = listOf(
                ReportGroup("Need", RecordFilter.Companion.byLabels("need")),
                ReportGroup("Want", RecordFilter.Companion.byLabels("want"))
            ),
            reports = ReportsConfiguration()
                .withGroupedBudget(
                    enabled = true,
                    distributions = listOf(
                        needWantDistribution(true, null, 60, 40),
                    ),
                ),
            forecast = ForecastConfiguration(3)
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration, expensesFundId))
        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON))).thenReturn(BigDecimal.ONE)

        val interval = ReportDataInterval.Monthly(
            YearMonth(2020, 1),
            YearMonth(2020, 4),
            YearMonth(2020, 6)
        )
        mockTransactions(
            interval, expensesFundId, listOf(
                // first month
                ronTransaction(LocalDate(2020, 1, 5), 2000, labelsOf()),
                ronTransaction(LocalDate(2020, 1, 10), -500, labelsOf("need")),
                ronTransaction(LocalDate(2020, 1, 12), -400, labelsOf("want")),
                // second month
                ronTransaction(LocalDate(2020, 2, 5), 2500, labelsOf()),
                ronTransaction(LocalDate(2020, 2, 20), -600, labelsOf("need")),
                ronTransaction(LocalDate(2020, 2, 21), -300, labelsOf("want")),
                // third month
                ronTransaction(LocalDate(2020, 3, 5), 1500, labelsOf()),
                ronTransaction(LocalDate(2020, 3, 20), -700, labelsOf("need")),
                ronTransaction(LocalDate(2020, 3, 21), -300, labelsOf("want")),
                // fourth month
                // second month
                ronTransaction(LocalDate(2020, 4, 5), 2000, labelsOf()),
                ronTransaction(LocalDate(2020, 4, 20), -600, labelsOf("need")),
                ronTransaction(LocalDate(2020, 4, 21), -400, labelsOf("want")),
            )
        )

        val data = reportDataService.getGroupedBudgetReport(userId, reportViewId, interval)

        assertThat(data.buckets).hasSize(6)

        val acceptedOffset = within(BigDecimal("0.01"))

        val forecastMay = data.buckets[4]
        assertThat(forecastMay.bucketType).isEqualTo(BucketType.FORECAST)
        val forecastBudgetMay = forecastMay.report
        assertThat(forecastBudgetMay["Need"]).isNotNull
        forecastBudgetMay["Need"]?.let {
            assertThat(it.allocated).isCloseTo(BigDecimal((2500 + 2000 + 1500) * 0.6 / 3.0), acceptedOffset)
            assertThat(it.spent).isCloseTo(BigDecimal((-600 - 700 - 600) / 3.0), acceptedOffset)
            assertThat(it.left).isCloseTo(
                BigDecimal(2400 + ((2500 + 2000 + 1500) * 0.6 - 600 - 700 - 600) / 3.0),
                acceptedOffset
            )
        }
    }

    @Test
    fun `get monthly value data with single currency`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            groups = null,
            reports = ReportsConfiguration()
                .withNet(enabled = true, filter = RecordFilter(labels = allLabels))
                .withValueReport(enabled = true),
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration, expensesFundId))
        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON))).thenReturn(BigDecimal.ONE)
        val interval = ReportDataInterval.Monthly(YearMonth(2021, 9), YearMonth(2021, 11))
        mockTransactions(
            interval, expensesFundId, listOf(
                ronTransaction(LocalDate.parse("2021-08-02"), 100, labelsOf("need")),
                ronTransaction(LocalDate.parse("2021-09-02"), 200, labelsOf("need")),
                ronTransaction(LocalDate.parse("2021-09-03"), -100, labelsOf("need")),
                ronTransaction(LocalDate.parse("2021-09-15"), -40, labelsOf("want")),
                ronTransaction(LocalDate.parse("2021-10-07"), 400, labelsOf("want")),
                ronTransaction(LocalDate.parse("2021-10-07"), -30, labelsOf("want")),
                ronTransaction(LocalDate.parse("2021-10-08"), -16, labelsOf("other")),
            )
        )

        val data = reportDataService.getValueReport(userId, reportViewId, interval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.interval).isEqualTo(interval)
        assertThat(data.buckets[0].report.start)
            .isEqualByComparingTo(BigDecimal("100.0"))
        assertThat(data.buckets[0].report.end)
            .isEqualByComparingTo(BigDecimal("160.0"))
        assertThat(data.buckets[1].report.start)
            .isEqualByComparingTo(BigDecimal("160.0"))
        assertThat(data.buckets[1].report.end)
            .isEqualByComparingTo(BigDecimal("514.0"))
        assertThat(data.buckets[2].report.start)
            .isEqualByComparingTo(BigDecimal("514.0"))
        assertThat(data.buckets[2].report.end)
            .isEqualByComparingTo(BigDecimal("514.0"))
    }

    @Test
    fun `get monthly value data with multiple currencies`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            groups = null,
            reports = ReportsConfiguration()
                .withValueReport(enabled = true),
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration, expensesFundId))
        val interval = ReportDataInterval.Monthly(YearMonth(2021, 9), YearMonth(2021, 10))
        mockTransactions(
            interval, expensesFundId, listOf(
                ronTransaction(LocalDate.parse("2021-08-02"), 100, labelsOf("need")),
                eurTransaction(LocalDate.parse("2021-08-05"), 20, labelsOf("need")),
                ronTransaction(LocalDate.parse("2021-09-02"), 100, labelsOf("need")),
                eurTransaction(LocalDate.parse("2021-09-03"), 20, labelsOf("need")),
            )
        )
        whenever(conversionRateService.getRate(any(), eq(EUR), eq(RON))).thenAnswer {
            val date = it.getArgument<LocalDate>(0)
            when (date) {
                LocalDate(2021, 9, 1) -> BigDecimal("4.85")
                LocalDate(2021, 9, 30) -> BigDecimal("4.9")
                LocalDate(2021, 10, 1) -> BigDecimal("4.95")
                LocalDate(2021, 10, 31) -> BigDecimal("5.0")
                else -> error("unexpected")
            }
        }
        whenever(conversionRateService.getRate(any(), eq(RON), eq(RON)))
            .thenAnswer { BigDecimal.ONE }

        val data = reportDataService.getValueReport(userId, reportViewId, interval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.interval).isEqualTo(interval)
        assertThat(data.buckets[0].timeBucket)
            .isEqualTo(TimeBucket(LocalDate(2021, 9, 1), LocalDate(2021, 9, 30)))
        assertThat(data.buckets[0].report.start).isEqualByComparingTo(BigDecimal("197.0"))
        assertThat(data.buckets[0].report.end).isEqualByComparingTo(
            BigDecimal("200.0") + BigDecimal("4.9") * BigDecimal(
                "40.0"
            )
        )
        assertThat(data.buckets[1].timeBucket)
            .isEqualTo(TimeBucket(LocalDate(2021, 10, 1), LocalDate(2021, 10, 31)))
        assertThat(data.buckets[1].report.start).isEqualByComparingTo(
            BigDecimal("200.0") + BigDecimal("4.95") * BigDecimal(
                "40.0"
            )
        )
        assertThat(data.buckets[1].report.end).isEqualByComparingTo(
            BigDecimal("200.0") + BigDecimal("5.0") * BigDecimal(
                "40.0"
            )
        )
    }

    @Test
    fun `get investment performance data`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = EUR,
            groups = null,
            reports = ReportsConfiguration()
                .withPerformanceReport(enabled = true),
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration, investmentFundId))
        val interval = ReportDataInterval.Monthly(
            YearMonth(2022, 5),
            YearMonth(2022, 6),
            YearMonth(2022, 8)
        )
        mockTransactions(
            interval, investmentFundId, listOf(
                investmentEurTransfer(LocalDate.parse("2022-04-15"), 400),
                investmentOpenPosition(LocalDate.parse("2022-04-18"), 300, Instrument("I1"), 1),
                investmentOpenPosition(LocalDate.parse("2022-04-18"), 90, Instrument("I2"), 3),

                investmentEurTransfer(LocalDate.parse("2022-05-15"), 400),
                investmentOpenPosition(LocalDate.parse("2022-05-18"), 290, Instrument("I1"), 1),
                investmentOpenPosition(LocalDate.parse("2022-05-18"), 96, Instrument("I2"), 3),

                investmentEurTransfer(LocalDate.parse("2022-06-15"), 400),
                investmentOpenPosition(LocalDate.parse("2022-06-18"), 305, Instrument("I1"), 1),
                investmentOpenPosition(LocalDate.parse("2022-06-18"), 102, Instrument("I2"), 3),
            )
        )
        whenever(conversionRateService.getRate(any(), any(), eq(EUR))).thenAnswer {
            val date: LocalDate = it.getArgument(0)
            val sourceUnit: FinancialUnit = it.getArgument(1)
            val targetUnit: Currency = it.getArgument(2)
            if (sourceUnit == targetUnit) return@thenAnswer BigDecimal.ONE
            when (sourceUnit) {
                Instrument("I1") -> when (date.month) {
                    Month.APRIL -> BigDecimal("298")
                    Month.MAY -> BigDecimal("289")
                    Month.JUNE -> BigDecimal("303")
                    else -> error("unexpected")
                }

                Instrument("I2") -> when (date.month) {
                    Month.APRIL -> BigDecimal("29")
                    Month.MAY -> BigDecimal("31")
                    Month.JUNE -> BigDecimal("33")
                    else -> error("unexpected")
                }

                else -> error("unexpected")
            }
        }

        val data = reportDataService.getPerformanceReport(userId, reportViewId, interval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.interval).isEqualTo(interval)
        assertThat(data.buckets).hasSize(4)

        assertThat(data.buckets[0].timeBucket)
            .isEqualTo(TimeBucket(LocalDate(2022, 5, 1), LocalDate(2022, 5, 31)))
        // 2 * 289 + 6 * 31 = 764
        assertThat(data.buckets[0].report.totalAssetsValue).isEqualByComparingTo(BigDecimal(764))
        // 400 - 300 - 90 + 400 - 290 - 96 = 24
        assertThat(data.buckets[0].report.totalCurrencyValue).isEqualByComparingTo(BigDecimal(24))
        // 300 + 90 + 290 + 96 = 776
        assertThat(data.buckets[0].report.totalInvestment).isEqualByComparingTo(BigDecimal(776))
        // 764 - 776 = -12
        assertThat(data.buckets[0].report.totalProfit).isEqualByComparingTo(BigDecimal(-12))
        // 290 + 96 = 386
        assertThat(data.buckets[0].report.currentInvestment).isEqualByComparingTo(BigDecimal(386))
        // -12 - (298 + 3 * 29 - 300 - 90) = -7
        assertThat(data.buckets[0].report.currentProfit).isEqualByComparingTo(BigDecimal(-7))

        assertThat(data.buckets[1].timeBucket)
            .isEqualTo(TimeBucket(LocalDate(2022, 6, 1), LocalDate(2022, 6, 30)))
        // 24 + 400 - 305 - 102 = 17
        assertThat(data.buckets[1].report.totalCurrencyValue).isEqualByComparingTo(BigDecimal(17))
        // 3 * 303 + 9 * 33 = 1206
        assertThat(data.buckets[1].report.totalAssetsValue).isEqualByComparingTo(BigDecimal(1206))
        // 776 + 305 + 102 = 1183
        assertThat(data.buckets[1].report.totalInvestment).isEqualByComparingTo(BigDecimal(1183))
        // 1206 - 1183 = 23
        assertThat(data.buckets[1].report.totalProfit).isEqualByComparingTo(BigDecimal(23))
        // 305 + 102 = 407
        assertThat(data.buckets[1].report.currentInvestment).isEqualByComparingTo(BigDecimal(407))
        // 23 - -12 = 35
        assertThat(data.buckets[1].report.currentProfit).isEqualByComparingTo(BigDecimal(35))
    }

    @Test
    fun `get investment unit performance data`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = EUR,
            groups = null,
            reports = ReportsConfiguration()
                .withInstrumentPerformanceReport(enabled = true),
            forecast = ForecastConfiguration(2)
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration, investmentFundId))
        val interval = ReportDataInterval.Monthly(
            YearMonth(2022, 5),
            YearMonth(2022, 6),
            YearMonth(2022, 8)
        )
        mockTransactions(
            interval, investmentFundId, listOf(
                investmentEurTransfer(LocalDate.parse("2022-04-15"), 400),
                investmentOpenPosition(LocalDate.parse("2022-04-18"), 300, Instrument("I1"), 1),
                investmentOpenPosition(LocalDate.parse("2022-04-18"), 90, Instrument("I2"), 3),

                investmentEurTransfer(LocalDate.parse("2022-05-15"), 400),
                investmentOpenPosition(LocalDate.parse("2022-05-18"), 290, Instrument("I1"), 1),
                investmentOpenPosition(LocalDate.parse("2022-05-18"), 96, Instrument("I2"), 3),

                investmentEurTransfer(LocalDate.parse("2022-06-15"), 400),
                investmentOpenPosition(LocalDate.parse("2022-06-18"), 305, Instrument("I1"), 1),
                investmentOpenPosition(LocalDate.parse("2022-06-18"), 102, Instrument("I2"), 3),
            )
        )
        whenever(conversionRateService.getRate(any(), any(), eq(EUR))).thenAnswer {
            val date: LocalDate = it.getArgument(0)
            val sourceUnit: FinancialUnit = it.getArgument(1)
            val targetUnit: Currency = it.getArgument(2)
            if (sourceUnit == targetUnit) return@thenAnswer BigDecimal.ONE
            when (sourceUnit) {
                Instrument("I1") -> when (date.month) {
                    Month.APRIL -> BigDecimal("298")
                    Month.MAY -> BigDecimal("289")
                    Month.JUNE -> BigDecimal("303")
                    else -> error("unexpected")
                }

                Instrument("I2") -> when (date.month) {
                    Month.APRIL -> BigDecimal("29")
                    Month.MAY -> BigDecimal("31")
                    Month.JUNE -> BigDecimal("33")
                    else -> error("unexpected")
                }

                else -> error("unexpected")
            }
        }

        val data = reportDataService.getInstrumentPerformanceReport(userId, reportViewId, interval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.interval).isEqualTo(interval)
        assertThat(data.buckets).hasSize(4)

        assertThat(data.buckets[0].timeBucket)
            .isEqualTo(TimeBucket(LocalDate(2022, 5, 1), LocalDate(2022, 5, 31)))
        assertThat(data.buckets[0].bucketType).isEqualTo(BucketType.REAL)
        val reportI1Bucket0 = data.buckets[0].report[Instrument("I1")]!!
        assertThat(reportI1Bucket0.totalValue).isEqualByComparingTo(BigDecimal(578)) // 2 * 289 = 578
        assertThat(reportI1Bucket0.totalInvestment).isEqualByComparingTo(BigDecimal(590)) // 300 + 290 = 590
        assertThat(reportI1Bucket0.totalProfit).isEqualByComparingTo(BigDecimal(-12)) // 590 - 578 = -12
        assertThat(reportI1Bucket0.currentInvestment).isEqualByComparingTo(BigDecimal(290)) // 290
        assertThat(reportI1Bucket0.currentProfit).isEqualByComparingTo(BigDecimal(-10)) // -12 - (298 - 300) = -10

        val reportI2Bucket0 = data.buckets[0].report[Instrument("I2")]!!
        assertThat(reportI2Bucket0.totalInvestment).isEqualByComparingTo(BigDecimal(186)) // 90 + 96 = 186
        assertThat(reportI2Bucket0.totalProfit).isEqualByComparingTo(BigDecimal(0)) // 186 - 186 = 0
        assertThat(reportI2Bucket0.currentInvestment).isEqualByComparingTo(BigDecimal(96)) // 96
        assertThat(reportI2Bucket0.currentProfit).isEqualByComparingTo(BigDecimal(3)) // 0 - (3 * 29 - 90) = 3

        assertThat(data.buckets[1].timeBucket)
            .isEqualTo(TimeBucket(LocalDate(2022, 6, 1), LocalDate(2022, 6, 30)))
        assertThat(data.buckets[1].bucketType).isEqualTo(BucketType.REAL)
        val reportI1Bucket1 = data.buckets[1].report[Instrument("I1")]!!
        assertThat(reportI1Bucket1.totalValue).isEqualByComparingTo(BigDecimal(909)) // 3 * 303 = 909
        assertThat(reportI1Bucket1.totalInvestment).isEqualByComparingTo(BigDecimal(895)) // 590 + 305 = 883
        assertThat(reportI1Bucket1.totalProfit).isEqualByComparingTo(BigDecimal(14)) // 909 - 895 = 14
        assertThat(reportI1Bucket1.currentInvestment).isEqualByComparingTo(BigDecimal(305)) // 305
        assertThat(reportI1Bucket1.currentProfit).isEqualByComparingTo(BigDecimal(26)) // 14 - -12 = 38

        assertThat(data.buckets[2].timeBucket)
            .isEqualTo(TimeBucket(LocalDate(2022, 7, 1), LocalDate(2022, 7, 31)))
        assertThat(data.buckets[2].bucketType).isEqualTo(BucketType.FORECAST)
        val reportI1Bucket2 = data.buckets[2].report[Instrument("I1")]!!
        assertThat(reportI1Bucket2.currentProfit).isEqualByComparingTo(BigDecimal(8)) // -10 + 26 / 2 = 8
        assertThat(reportI1Bucket2.currentInvestment).isEqualByComparingTo(BigDecimal(297.5)) // (290) + 305) / 2 = 297.5
        assertThat(reportI1Bucket2.totalValue).isEqualByComparingTo(BigDecimal(909 + 8 + 297.5))
        assertThat(reportI1Bucket2.totalInvestment).isEqualByComparingTo(BigDecimal(895 + 297.5))
        assertThat(reportI1Bucket2.totalProfit).isEqualByComparingTo(BigDecimal(14 + 8))
    }

    @Test
    fun `get net data when net feature is disabled should throw exception`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = RON,
            groups = null,
            reports = ReportsConfiguration()
                .withNet(enabled = false)
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration, expensesFundId))
        val interval = ReportDataInterval.Monthly(YearMonth(2021, 9), YearMonth(2021, 11))

        assertThatThrownBy {
            runBlocking { reportDataService.getNetReport(userId, reportViewId, interval) }
        }
            .isInstanceOf(ReportingException.FeatureDisabled::class.java)
    }

    @Test
    fun `get net data when report view not found should throw exception`(): Unit = runBlocking {
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(null)
        val interval = ReportDataInterval.Monthly(YearMonth(2021, 9), YearMonth(2021, 11))

        assertThatThrownBy {
            runBlocking { reportDataService.getNetReport(userId, reportViewId, interval) }
        }
            .isInstanceOf(ReportingException.ReportViewNotFound::class.java)
    }

    @Test
    fun `get interest rate report`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = EUR,
            groups = null,
            reports = ReportsConfiguration()
                .withInterestRate(enabled = true),
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration, investmentFundId))
        val interval = ReportDataInterval.Monthly(
            YearMonth(2022, 5),
            YearMonth(2022, 6)
        )
        mockTransactions(
            interval, investmentFundId, listOf(
                investmentEurTransfer(LocalDate.parse("2022-04-15"), 400),
                investmentOpenPosition(LocalDate.parse("2022-04-18"), 300, Instrument("I1"), 1),

                investmentEurTransfer(LocalDate.parse("2022-05-15"), 400),
                investmentOpenPosition(LocalDate.parse("2022-05-18"), 290, Instrument("I1"), 1),

                investmentEurTransfer(LocalDate.parse("2022-06-15"), 400),
                investmentOpenPosition(LocalDate.parse("2022-06-18"), 305, Instrument("I1"), 1),
            )
        )
        whenever(conversionRateService.getRate(any(), eq(EUR), eq(EUR))).thenReturn(BigDecimal.ONE)
        whenever(conversionRateService.getRate(any(), eq(Instrument("I1")), eq(EUR))).thenReturn(
            BigDecimal(
                300
            )
        )

        val data = reportDataService.getInterestRateReport(userId, reportViewId, interval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.interval).isEqualTo(interval)
        assertThat(data.buckets).hasSize(2)

        assertThat(data.buckets[0].timeBucket)
            .isEqualTo(TimeBucket(LocalDate(2022, 5, 1), LocalDate(2022, 5, 31)))
        assertThat(data.buckets[0].report.totalInterestRate).isPositive
        assertThat(data.buckets[0].report.currentInterestRate).isPositive

        assertThat(data.buckets[1].timeBucket)
            .isEqualTo(TimeBucket(LocalDate(2022, 6, 1), LocalDate(2022, 6, 30)))
        assertThat(data.buckets[1].report.totalInterestRate).isPositive
        assertThat(data.buckets[1].report.currentInterestRate).isNegative
    }

    @Test
    fun `get instrument interest rate report`(): Unit = runBlocking {
        val reportDataConfiguration = ReportDataConfiguration(
            currency = EUR,
            groups = null,
            reports = ReportsConfiguration()
                .withInstrumentInterestRate(enabled = true),
        )
        whenever(reportViewRepository.findById(userId, reportViewId))
            .thenReturn(reportView(reportDataConfiguration, investmentFundId))
        val interval = ReportDataInterval.Monthly(
            YearMonth(2022, 5),
            YearMonth(2022, 6)
        )
        mockTransactions(
            interval, investmentFundId, listOf(
                investmentEurTransfer(LocalDate.parse("2022-04-15"), 400),
                investmentOpenPosition(LocalDate.parse("2022-04-18"), 300, Instrument("I1"), 1),
                investmentOpenPosition(LocalDate.parse("2022-04-18"), 90, Instrument("I2"), 3),

                investmentEurTransfer(LocalDate.parse("2022-05-15"), 400),
                investmentOpenPosition(LocalDate.parse("2022-05-18"), 290, Instrument("I1"), 1),
                investmentOpenPosition(LocalDate.parse("2022-05-18"), 96, Instrument("I2"), 3),

                investmentEurTransfer(LocalDate.parse("2022-06-15"), 400),
                investmentOpenPosition(LocalDate.parse("2022-06-18"), 305, Instrument("I1"), 1),
                investmentOpenPosition(LocalDate.parse("2022-06-18"), 102, Instrument("I2"), 3),
            )
        )
        whenever(conversionRateService.getRate(any(), eq(EUR), eq(EUR))).thenReturn(BigDecimal.ONE)
        whenever(conversionRateService.getRate(any(), eq(Instrument("I1")), eq(EUR))).thenReturn(
            BigDecimal(
                306
            )
        )
        whenever(conversionRateService.getRate(any(), eq(Instrument("I2")), eq(EUR))).thenReturn(
            BigDecimal(
                29
            )
        )

        val data = reportDataService.getInstrumentInterestRateReport(userId, reportViewId, interval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.interval).isEqualTo(interval)
        assertThat(data.buckets).hasSize(2)

        assertThat(data.buckets[0].timeBucket)
            .isEqualTo(TimeBucket(LocalDate(2022, 5, 1), LocalDate(2022, 5, 31)))
        val reportI1Bucket0 = data.buckets[0].report[Instrument("I1")]!!
        assertThat(reportI1Bucket0.instrument).isEqualTo(Instrument("I1"))
        assertThat(reportI1Bucket0.totalInterestRate).isPositive()
        assertThat(reportI1Bucket0.currentInterestRate).isPositive()

        val reportI2Bucket0 = data.buckets[0].report[Instrument("I2")]!!
        assertThat(reportI2Bucket0.instrument).isEqualTo(Instrument("I2"))
        assertThat(reportI2Bucket0.totalInterestRate).isNegative
        assertThat(reportI2Bucket0.currentInterestRate).isNegative

        assertThat(data.buckets[1].timeBucket)
            .isEqualTo(TimeBucket(LocalDate(2022, 6, 1), LocalDate(2022, 6, 30)))
        val reportI1Bucket1 = data.buckets[1].report[Instrument("I1")]!!
        assertThat(reportI1Bucket1.totalInterestRate).isPositive
        assertThat(reportI1Bucket1.currentInterestRate).isPositive

        val reportI2Bucket1 = data.buckets[1].report[Instrument("I2")]!!
        assertThat(reportI2Bucket1.totalInterestRate).isNegative
        assertThat(reportI2Bucket1.currentInterestRate).isNegative
    }

    private fun reportView(
        dataConfiguration: ReportDataConfiguration,
        fundId: UUID,
    ) = ReportView(reportViewId, userId, reportViewName, fundId, dataConfiguration)

    private fun ronTransaction(
        date: LocalDate, amount: Int, labels: List<Label>,
    ) = transaction(date, RON, BigDecimal(amount), labels)

    private fun eurTransaction(
        date: LocalDate, amount: Int, labels: List<Label>,
    ) = transaction(date, EUR, BigDecimal(amount), labels)

    private fun investmentEurTransfer(date: LocalDate, amountEur: Int) =
        TransactionTO.Transfer(
            id = randomUUID(),
            userId = userId,
            dateTime = date.atTime(12, 0),
            externalId = randomUUID().toString(),
            sourceRecord = TransactionRecordTO(
                id = randomUUID(),
                fundId = expensesFundId,
                accountId = randomUUID(),
                amount = BigDecimal(amountEur * -1),
                unit = EUR,
                labels = labelsOf("investment")
            ),
            destinationRecord = TransactionRecordTO(
                id = randomUUID(),
                fundId = investmentFundId,
                accountId = randomUUID(),
                amount = BigDecimal(amountEur),
                unit = EUR,
                labels = labelsOf("investment")
            )
        )

    private fun investmentOpenPosition(date: LocalDate, eurAmount: Int, instrument: Instrument, instrumentAmount: Int) =
        TransactionTO.OpenPosition(
            id = randomUUID(),
            userId = userId,
            dateTime = date.atTime(12, 0),
            externalId = randomUUID().toString(),
            currencyRecord = TransactionRecordTO(
                id = randomUUID(),
                fundId = investmentFundId,
                accountId = randomUUID(),
                amount = BigDecimal(eurAmount * -1),
                unit = EUR,
                labels = emptyList(),
            ),
            instrumentRecord = TransactionRecordTO(
                id = randomUUID(),
                fundId = investmentFundId,
                accountId = randomUUID(),
                amount = BigDecimal(instrumentAmount),
                unit = instrument,
                labels = emptyList(),
            )
        )

    private fun transaction(
        date: LocalDate, unit: FinancialUnit, amount: BigDecimal, labels: List<Label>,
    ) = TransactionTO.SingleRecord(
        id = randomUUID(),
        userId = userId,
        dateTime = date.atTime(12, 0),
        externalId = randomUUID().toString(),
        record = TransactionRecordTO(
            id = randomUUID(),
            fundId = expensesFundId,
            unit = unit,
            amount = amount,
            labels = labels,
            accountId = randomUUID()
        )
    )

    private suspend fun mockTransactions(
        interval: ReportDataInterval, fundId: UUID, transactions: List<TransactionTO>,
    ) {
        whenever(
            fundTransactionSdk.listTransactions(
                userId,
                TransactionFilterTO(null, interval.getPreviousLastDay(), fundId)
            )
        ).thenReturn(ListTO(transactions.filter { it.dateTime.date <= interval.getPreviousLastDay() }))
        interval.getBuckets().forEach { bucket ->
            whenever(
                fundTransactionSdk.listTransactions(
                    userId,
                    TransactionFilterTO(bucket.from, bucket.to, fundId)
                )
            ).thenReturn(ListTO(transactions.filter { it.dateTime.date >= bucket.from && it.dateTime.date <= bucket.to }))
        }
    }

    private fun needWantDistribution(
        default: Boolean, from: YearMonth?, needPercentage: Int, wantPercentage: Int,
    ) = GroupedBudgetReportConfiguration.BudgetDistribution(
        default, from, listOf(
            GroupedBudgetReportConfiguration.GroupBudgetPercentage("Need", needPercentage),
            GroupedBudgetReportConfiguration.GroupBudgetPercentage("Want", wantPercentage),
        )
    )
}