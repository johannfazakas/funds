package ro.jf.funds.analytics.service.service.series

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import ro.jf.funds.analytics.api.model.GroupingCriteria
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.domain.Series
import ro.jf.funds.analytics.service.domain.GroupKey
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.UnitAmounts
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument

class InvestmentMetricsTest : MetricResolutionTestBase() {

    private val vt = Instrument("VT")
    private val interval = ReportInterval(
        granularity = TimeGranularity.MONTHLY,
        from = LocalDateTime.parse("2024-01-01T00:00:00"),
        to = LocalDateTime.parse("2024-03-01T00:00:00"),
    )
    private val performanceMetrics = listOf(
        Series.TotalInvestment,
        Series.CurrentInvestment,
        Series.TotalInstrumentValue,
        Series.CurrencyValue,
        Series.TotalProfit,
        Series.CurrentProfit,
    )

    @Test
    fun `given open position records - when resolving performance metrics - then returns correct investment and instrument values`(): Unit =
        runBlocking {
            whenever(analyticsRecordRepository.getRecords(any(), any(), eq(openPositionFilter)))
                .thenReturn(openPositionRecords(
                    dateTime = LocalDateTime.parse("2024-01-15T00:00:00"),
                    currencyUnit = Currency.RON, currencyAmount = "-1000.00",
                    instrumentUnit = vt, instrumentAmount = "10.00",
                ))
            whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(instrumentFilter), isNull()))
                .thenReturn(ungroupedBuckets(
                    LocalDateTime.parse("2024-01-01T00:00:00") to
                        UnitAmounts(mapOf(vt to BigDecimal.parseString("10.00"))),
                ))
            whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(currencyFilter), isNull()))
                .thenReturn(ungroupedBuckets(
                    LocalDateTime.parse("2024-01-01T00:00:00") to
                        UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("-1000.00"))),
                ))
            givenRate(vt, Currency.RON, "2024-01-01", "120.00")
            givenRate(vt, Currency.RON, "2024-02-01", "130.00")

            val resolved = resolve(performanceMetrics, interval)

            assertThat(resolved[Series.TotalInvestment].value("2024-01-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("1000.00"))
            assertThat(resolved[Series.CurrentInvestment].value("2024-01-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("1000.00"))
            assertThat(resolved[Series.TotalInstrumentValue].value("2024-01-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("1200.00"))
            assertThat(resolved[Series.CurrencyValue].value("2024-01-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("-1000.00"))
            assertThat(resolved[Series.TotalProfit].value("2024-01-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("200.00"))
            assertThat(resolved[Series.CurrentProfit].value("2024-01-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("200.00"))

            assertThat(resolved[Series.TotalInvestment].value("2024-02-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("1000.00"))
            assertThat(resolved[Series.CurrentInvestment].value("2024-02-01T00:00:00"))
                .isEqualTo(BigDecimal.ZERO)
            assertThat(resolved[Series.TotalInstrumentValue].value("2024-02-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("1300.00"))
            assertThat(resolved[Series.CurrencyValue].value("2024-02-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("-1000.00"))
            assertThat(resolved[Series.TotalProfit].value("2024-02-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("300.00"))
            assertThat(resolved[Series.CurrentProfit].value("2024-02-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("100.00"))
        }

    @Test
    fun `given previous positions and new bucket positions - when resolving investment metrics - then accumulates over buckets`(): Unit =
        runBlocking {
            whenever(analyticsRecordRepository.getRecordsBefore(any(), any(), eq(openPositionFilter)))
                .thenReturn(openPositionRecords(
                    dateTime = LocalDateTime.parse("2023-12-15T00:00:00"),
                    currencyUnit = Currency.RON, currencyAmount = "-500.00",
                    instrumentUnit = vt, instrumentAmount = "5.00",
                ))
            whenever(analyticsRecordRepository.getRecords(any(), any(), eq(openPositionFilter)))
                .thenReturn(openPositionRecords(
                    dateTime = LocalDateTime.parse("2024-02-15T00:00:00"),
                    currencyUnit = Currency.RON, currencyAmount = "-300.00",
                    instrumentUnit = vt, instrumentAmount = "3.00",
                ))
            whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(instrumentFilter), isNull()))
                .thenReturn(ungroupedAmounts(UnitAmounts(mapOf(vt to BigDecimal.parseString("5.00")))))
            whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(instrumentFilter), isNull()))
                .thenReturn(ungroupedBuckets(
                    LocalDateTime.parse("2024-02-01T00:00:00") to
                        UnitAmounts(mapOf(vt to BigDecimal.parseString("3.00"))),
                ))
            givenRate(vt, Currency.RON, "2024-01-01", "110.00")
            givenRate(vt, Currency.RON, "2024-02-01", "120.00")

            val resolved = resolve(
                listOf(Series.TotalInvestment, Series.TotalInstrumentValue, Series.TotalProfit),
                interval,
            )

            assertThat(resolved[Series.TotalInvestment].value("2024-01-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("500.00"))
            assertThat(resolved[Series.TotalInstrumentValue].value("2024-01-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("550.00"))
            assertThat(resolved[Series.TotalProfit].value("2024-01-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("50.00"))

            assertThat(resolved[Series.TotalInvestment].value("2024-02-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("800.00"))
            assertThat(resolved[Series.TotalInstrumentValue].value("2024-02-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("960.00"))
            assertThat(resolved[Series.TotalProfit].value("2024-02-01T00:00:00"))
                .isEqualTo(BigDecimal.parseString("160.00"))
        }

    @Test
    fun `given cross-currency investment - when resolving total investment - then converts at transaction date`(): Unit =
        runBlocking {
            whenever(analyticsRecordRepository.getRecords(any(), any(), eq(openPositionFilter)))
                .thenReturn(openPositionRecords(
                    dateTime = LocalDateTime.parse("2024-01-15T00:00:00"),
                    currencyUnit = Currency.EUR, currencyAmount = "-200.00",
                    instrumentUnit = vt, instrumentAmount = "2.00",
                ))
            givenRate(Currency.EUR, Currency.RON, "2024-01-15", "5.00")

            val report = resolve(listOf(Series.TotalInvestment), interval)
            val totalInvestment = report[Series.TotalInvestment]

            assertThat(totalInvestment.value("2024-01-01T00:00:00")).isEqualTo(BigDecimal.parseString("1000.00"))
            assertThat(totalInvestment.value("2024-02-01T00:00:00")).isEqualTo(BigDecimal.parseString("1000.00"))
        }

    @Test
    fun `given grouping by financial unit - when resolving total investment - then investment attributed to paired instrument group`(): Unit =
        runBlocking {
            val sxr8 = Instrument("SXR8")
            whenever(analyticsRecordRepository.getRecords(any(), any(), eq(openPositionFilter)))
                .thenReturn(
                    openPositionRecords(
                        dateTime = LocalDateTime.parse("2024-01-10T00:00:00"),
                        currencyUnit = Currency.RON, currencyAmount = "-1000.00",
                        instrumentUnit = vt, instrumentAmount = "10.00",
                    ) + openPositionRecords(
                        dateTime = LocalDateTime.parse("2024-01-20T00:00:00"),
                        currencyUnit = Currency.RON, currencyAmount = "-600.00",
                        instrumentUnit = sxr8, instrumentAmount = "2.00",
                    )
                )

            val totalInvestment = resolve(
                listOf(Series.TotalInvestment), interval, groupBy = GroupingCriteria.FINANCIAL_UNIT,
            )[Series.TotalInvestment]

            assertThat(totalInvestment.value("2024-01-01T00:00:00", GroupKey.ByFinancialUnit("VT")))
                .isEqualTo(BigDecimal.parseString("1000.00"))
            assertThat(totalInvestment.value("2024-01-01T00:00:00", GroupKey.ByFinancialUnit("SXR8")))
                .isEqualTo(BigDecimal.parseString("600.00"))
        }
}
