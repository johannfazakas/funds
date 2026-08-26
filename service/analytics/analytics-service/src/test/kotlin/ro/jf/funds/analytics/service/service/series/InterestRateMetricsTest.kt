package ro.jf.funds.analytics.service.service.series

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import ro.jf.funds.analytics.api.model.GroupingCriteria
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.domain.Series
import ro.jf.funds.analytics.service.domain.GroupKey
import ro.jf.funds.analytics.service.domain.GroupedUnitAmounts
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.UnitAmounts
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.Instrument

class InterestRateMetricsTest : MetricResolutionTestBase() {

    private val eur = Currency("EUR")
    private val sxr8 = Instrument("SXR8")
    private val singleYearInterval = ReportInterval(
        granularity = TimeGranularity.YEARLY,
        from = LocalDateTime.parse("2024-01-01T00:00:00"),
        to = LocalDateTime.parse("2025-01-01T00:00:00"),
    )

    @Test
    fun `given investment with 10 percent growth - when resolving total interest rate - then returns approximately 10 percent`(): Unit =
        runBlocking {
            whenever(analyticsRecordRepository.getRecordsBefore(any(), any(), eq(openPositionFilter)))
                .thenReturn(listOf(
                    analyticsRecord(
                        dateTime = LocalDateTime.parse("2023-01-01T10:00:00"), amount = "-1000.00", unit = eur,
                        transactionType = ro.jf.funds.fund.api.model.TransactionType.OPEN_POSITION,
                    )
                ))
            whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(instrumentFilter), isNull()))
                .thenReturn(ungroupedAmounts(UnitAmounts(mapOf(sxr8 to BigDecimal.parseString("10.00")))))
            givenRate(sxr8, eur, "2024-01-01", "110.00")

            val resolved = resolve(
                listOf(Series.TotalInterestRate, Series.CurrentInterestRate),
                singleYearInterval,
                targetCurrency = eur,
            )

            val totalRate = resolved[Series.TotalInterestRate]
            assertThat(totalRate.value("2024-01-01T00:00:00").doubleValue(false))
                .isCloseTo(10.0, Offset.offset(0.5))

            val currentRate = resolved[Series.CurrentInterestRate]
            assertThat(currentRate.value("2024-01-01T00:00:00")).isEqualTo(BigDecimal.ZERO)
        }

    @Test
    fun `given growth across two yearly buckets - when resolving current interest rate - then uses previous bucket valuation`(): Unit =
        runBlocking {
            val twoYearInterval = ReportInterval(
                granularity = TimeGranularity.YEARLY,
                from = LocalDateTime.parse("2024-01-01T00:00:00"),
                to = LocalDateTime.parse("2026-01-01T00:00:00"),
            )
            whenever(analyticsRecordRepository.getRecordsBefore(any(), any(), eq(openPositionFilter)))
                .thenReturn(listOf(
                    analyticsRecord(
                        dateTime = LocalDateTime.parse("2023-01-01T10:00:00"), amount = "-1000.00", unit = eur,
                        transactionType = ro.jf.funds.fund.api.model.TransactionType.OPEN_POSITION,
                    )
                ))
            whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(instrumentFilter), isNull()))
                .thenReturn(ungroupedAmounts(UnitAmounts(mapOf(sxr8 to BigDecimal.parseString("10.00")))))
            givenRate(sxr8, eur, "2024-01-01", "110.00")
            givenRate(sxr8, eur, "2025-01-01", "121.00")

            val resolved = resolve(
                listOf(Series.TotalInterestRate, Series.CurrentInterestRate),
                twoYearInterval,
                targetCurrency = eur,
            )

            val totalRate = resolved[Series.TotalInterestRate]
            assertThat(totalRate.value("2025-01-01T00:00:00").doubleValue(false))
                .isCloseTo(10.0, Offset.offset(0.5))

            val currentRate = resolved[Series.CurrentInterestRate]
            assertThat(currentRate.value("2025-01-01T00:00:00").doubleValue(false))
                .isCloseTo(10.0, Offset.offset(0.5))
        }

    @Test
    fun `given two funds with different growth - when resolving grouped total interest rate by fund - then returns per-fund rates`(): Unit =
        runBlocking {
            val vt = Instrument("VT")
            val otherFundId = com.benasher44.uuid.uuid4()
            val fund1 = GroupKey.ByFund(fundId.toString())
            val fund2 = GroupKey.ByFund(otherFundId.toString())

            whenever(analyticsRecordRepository.getRecordsBefore(any(), any(), eq(openPositionFilter)))
                .thenReturn(listOf(
                    analyticsRecord(
                        dateTime = LocalDateTime.parse("2023-01-01T10:00:00"), amount = "-1000.00", unit = eur,
                        transactionType = ro.jf.funds.fund.api.model.TransactionType.OPEN_POSITION,
                    ),
                    analyticsRecord(
                        dateTime = LocalDateTime.parse("2023-01-01T10:00:00"), amount = "-2000.00", unit = eur,
                        transactionType = ro.jf.funds.fund.api.model.TransactionType.OPEN_POSITION,
                        recordFundId = otherFundId,
                    ),
                ))
            whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(instrumentFilter), eq(GroupingCriteria.FUND)))
                .thenReturn(GroupedUnitAmounts(mapOf(
                    fund1 to UnitAmounts(mapOf(sxr8 to BigDecimal.parseString("10.00"))),
                    fund2 to UnitAmounts(mapOf(vt to BigDecimal.parseString("20.00"))),
                )))
            givenRate(sxr8, eur, "2024-01-01", "110.00")
            givenRate(vt, eur, "2024-01-01", "120.00")

            val totalRate = resolve(
                listOf(Series.TotalInterestRate),
                singleYearInterval,
                targetCurrency = eur,
                groupBy = GroupingCriteria.FUND,
            )[Series.TotalInterestRate]

            assertThat(totalRate.value("2024-01-01T00:00:00", fund1).doubleValue(false))
                .isCloseTo(10.0, Offset.offset(0.5))
            assertThat(totalRate.value("2024-01-01T00:00:00", fund2).doubleValue(false))
                .isCloseTo(20.0, Offset.offset(0.5))
        }
}
