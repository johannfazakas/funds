package ro.jf.funds.reporting.service.service.reportdata

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
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
            .isEqualTo(DateInterval(LocalDate.parse("2021-09-03"), LocalDate.parse("2021-09-30")))
        assertThat(data.data[0].aggregate.net).isEqualByComparingTo(BigDecimal("-300.0"))
        assertThat(data.data[1].timeBucket)
            .isEqualTo(DateInterval(LocalDate.parse("2021-10-01"), LocalDate.parse("2021-10-31")))
        assertThat(data.data[1].aggregate.net).isEqualByComparingTo(BigDecimal("-30.0"))
        assertThat(data.data[2].timeBucket)
            .isEqualTo(DateInterval(LocalDate.parse("2021-11-01"), LocalDate.parse("2021-11-25")))
        assertThat(data.data[2].aggregate.net).isEqualByComparingTo(BigDecimal.ZERO)
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
    fun `get grouped budget`(): Unit = runBlocking {
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
                        needWantDistribution(true, null, 50, 50),
                        needWantDistribution(false, YearMonth(2020, 1), 60, 40),
                        needWantDistribution(false, YearMonth(2020, 3), 70, 30),
                    ),
                )
        )
        whenever(reportViewRepository.findById(userId, reportViewId)).thenReturn(reportView(reportDataConfiguration))
        val interval = DateInterval(YearMonth(2020, 2), YearMonth(2020, 3))
        whenever(historicalPricingSdk.convert(eq(userId), any())).thenReturn(ConversionsResponse.empty())
        whenever(reportRecordRepository.findByViewUntil(userId, reportViewId, interval.to))
            .thenReturn(
                listOf(
                    // previous period with default distribution
                    ronReportRecord(LocalDate(2019, 12, 5), 3000, labelsOf()),
                    eurReportRecord(LocalDate(2019, 12, 10), 200, 1000, labelsOf()),
                    // previous month with specific distribution
                    ronReportRecord(LocalDate(2020, 1, 5), 1000, labelsOf()),
                    ronReportRecord(LocalDate(2020, 1, 10), -100, labelsOf("need")),
                    ronReportRecord(LocalDate(2020, 1, 15), -150, labelsOf("want")),
                    ronReportRecord(LocalDate(2020, 1, 18), -200, labelsOf("need")),
                    // month 1
                    ronReportRecord(LocalDate(2020, 2, 5), 1200, labelsOf()),
                    ronReportRecord(LocalDate(2020, 2, 10), -500, labelsOf("need")),
                    ronReportRecord(LocalDate(2020, 2, 15), -400, labelsOf("want")),
                    ronReportRecord(LocalDate(2020, 2, 18), -50, labelsOf("want")),
                    // month 2
                    ronReportRecord(LocalDate(2020, 3, 5), 1500, labelsOf()),
                    ronReportRecord(LocalDate(2020, 3, 5), -250, labelsOf("need")),
                    ronReportRecord(LocalDate(2020, 3, 5), -350, labelsOf("want")),
                )
            )
        val granularInterval = GranularDateInterval(interval, TimeGranularity.MONTHLY)

        val data = reportDataService.getReportViewData(userId, reportViewId, granularInterval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.granularInterval).isEqualTo(granularInterval)
        assertThat(data.data).hasSize(2)
        // TODO(Johann-14) also check data
        assertThat(data.data[0].timeBucket)
            .isEqualTo(DateInterval(YearMonth(2020, 2), YearMonth(2020, 2)))
        val groupedBudget1 = data.data[0].aggregate.groupedBudget
        assertThat(groupedBudget1?.get("Need")?.get(RON)?.left)
            .isEqualByComparingTo(BigDecimal(3000 * 0.5 + 1000 * 0.6 + 1200 * 0.6 - 100 - 200 - 500))
        assertThat(groupedBudget1?.get("Need")?.get(RON)?.allocated)
            .isEqualByComparingTo(BigDecimal(1200 * 0.6))
        assertThat(groupedBudget1?.get("Want")?.get(RON)?.left)
            .isEqualByComparingTo(BigDecimal(3000 * 0.5 + 1000 * 0.4 + 1200 * 0.4 - 150 - 400 - 50))
        assertThat(groupedBudget1?.get("Want")?.get(RON)?.allocated)
            .isEqualByComparingTo(BigDecimal(1200 * 0.4))

        assertThat(data.data[1].timeBucket)
            .isEqualTo(DateInterval(YearMonth(2020, 3), YearMonth(2020, 3)))
        val groupedBudget2 = data.data[1].aggregate.groupedBudget
        assertThat(groupedBudget2?.get("Need")?.get(RON)?.left)
            .isEqualByComparingTo(BigDecimal(3000 * 0.5 + 1000 * 0.6 + 1200 * 0.6 - 100 - 200 - 500 + 1500 * 0.7 - 250))
        assertThat(groupedBudget2?.get("Need")?.get(RON)?.allocated)
            .isEqualByComparingTo(BigDecimal(1500 * 0.7))
        assertThat(groupedBudget2?.get("Want")?.get(RON)?.left)
            .isEqualByComparingTo(BigDecimal(3000 * 0.5 + 1000 * 0.4 + 1200 * 0.4 + 1500 * 0.3 - 150 - 400 - 50 - 350))
        assertThat(groupedBudget2?.get("Want")?.get(RON)?.allocated)
            .isEqualByComparingTo(BigDecimal(1500 * 0.3))
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
                ConversionRequest(EUR, RON, LocalDate(2021, 9, 2)),
                ConversionRequest(EUR, RON, LocalDate(2021, 9, 30)),
                ConversionRequest(EUR, RON, LocalDate(2021, 10, 1)),
                ConversionRequest(EUR, RON, LocalDate(2021, 10, 30)),
            )
        )
        val conversionsResponse = ConversionsResponse(
            listOf(
                ConversionResponse(EUR, RON, LocalDate(2021, 9, 2), BigDecimal("4.85")),
                ConversionResponse(EUR, RON, LocalDate(2021, 9, 30), BigDecimal("4.9")),
                ConversionResponse(EUR, RON, LocalDate(2021, 10, 1), BigDecimal("4.95")),
                ConversionResponse(EUR, RON, LocalDate(2021, 10, 30), BigDecimal("5.0")),
            )
        )
        whenever(historicalPricingSdk.convert(eq(userId), any())).thenReturn(conversionsResponse)
        val granularInterval = GranularDateInterval(interval, TimeGranularity.MONTHLY)

        val data = reportDataService.getReportViewData(userId, reportViewId, granularInterval)

        assertThat(data.reportViewId).isEqualTo(reportViewId)
        assertThat(data.granularInterval).isEqualTo(granularInterval)
        assertThat(data.data[0].timeBucket)
            .isEqualTo(DateInterval(LocalDate(2021, 9, 2), LocalDate(2021, 9, 30)))
        assertThat(data.data[0].aggregate.value?.start).isEqualByComparingTo(BigDecimal("197.0"))
        assertThat(data.data[0].aggregate.value?.end).isEqualByComparingTo(
            BigDecimal("200.0") + BigDecimal("4.9") * BigDecimal(
                "40.0"
            )
        )
        assertThat(data.data[1].timeBucket)
            .isEqualTo(DateInterval(LocalDate(2021, 10, 1), LocalDate(2021, 10, 30)))
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
        assertThat(conversionsRequestCaptor.firstValue.conversions).containsExactlyInAnyOrderElementsOf(
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

    private fun budget(allocated: Int, left: Int): Budget = Budget(BigDecimal(allocated), BigDecimal(left))
}
