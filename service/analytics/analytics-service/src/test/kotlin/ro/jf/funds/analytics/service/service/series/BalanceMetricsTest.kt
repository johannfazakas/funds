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
import ro.jf.funds.analytics.service.domain.BucketedGroupedUnitAmounts
import ro.jf.funds.analytics.service.domain.Series
import ro.jf.funds.analytics.service.domain.GroupKey
import ro.jf.funds.analytics.service.domain.GroupedUnitAmounts
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.UnitAmounts
import ro.jf.funds.platform.api.model.Currency

class BalanceMetricsTest : MetricResolutionTestBase() {

    private val interval = ReportInterval(
        granularity = TimeGranularity.MONTHLY,
        from = LocalDateTime.parse("2024-01-01T00:00:00"),
        to = LocalDateTime.parse("2024-04-01T00:00:00"),
    )

    @Test
    fun `given previous balance and bucketed amounts - when resolving balance - then returns cumulative balance per bucket`(): Unit =
        runBlocking {
            whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(unfilteredFilter), isNull()))
                .thenReturn(ungroupedAmounts(UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("500.00")))))
            whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(unfilteredFilter), isNull()))
                .thenReturn(ungroupedBuckets(
                    LocalDateTime.parse("2024-01-01T00:00:00") to
                        UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("70.00"))),
                    LocalDateTime.parse("2024-03-01T00:00:00") to
                        UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("150.00"))),
                ))

            val balance = resolve(listOf(Series.Balance), interval)[Series.Balance]

            assertThat(balance.value("2024-01-01T00:00:00")).isEqualTo(BigDecimal.parseString("500.00"))
            assertThat(balance.value("2024-02-01T00:00:00")).isEqualTo(BigDecimal.parseString("570.00"))
            assertThat(balance.value("2024-03-01T00:00:00")).isEqualTo(BigDecimal.parseString("570.00"))
        }

    @Test
    fun `given bucketed amounts - when resolving net change - then returns per-bucket changes with zero-filled gaps`(): Unit =
        runBlocking {
            whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(unfilteredFilter), isNull()))
                .thenReturn(ungroupedBuckets(
                    LocalDateTime.parse("2024-01-01T00:00:00") to
                        UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("70.00"))),
                    LocalDateTime.parse("2024-03-01T00:00:00") to
                        UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("150.00"))),
                ))

            val netChange = resolve(listOf(Series.NetChange), interval)[Series.NetChange]

            assertThat(netChange.value("2024-01-01T00:00:00")).isEqualTo(BigDecimal.parseString("70.00"))
            assertThat(netChange.value("2024-02-01T00:00:00")).isEqualTo(BigDecimal.ZERO)
            assertThat(netChange.value("2024-03-01T00:00:00")).isEqualTo(BigDecimal.parseString("150.00"))
        }

    @Test
    fun `given multi-currency balances - when resolving balance - then converts each unit at bucket date`(): Unit =
        runBlocking {
            whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(unfilteredFilter), isNull()))
                .thenReturn(ungroupedAmounts(UnitAmounts(mapOf(
                    Currency.RON to BigDecimal.parseString("100.00"),
                    Currency.EUR to BigDecimal.parseString("10.00"),
                ))))
            givenRate(Currency.EUR, Currency.RON, "2024-01-01", "5.00")
            givenRate(Currency.EUR, Currency.RON, "2024-02-01", "6.00")
            givenRate(Currency.EUR, Currency.RON, "2024-03-01", "6.00")

            val balance = resolve(listOf(Series.Balance), interval)[Series.Balance]

            assertThat(balance.value("2024-01-01T00:00:00")).isEqualTo(BigDecimal.parseString("150.00"))
            assertThat(balance.value("2024-02-01T00:00:00")).isEqualTo(BigDecimal.parseString("160.00"))
        }

    @Test
    fun `given amounts in two funds - when resolving grouped balance by fund - then returns per-fund cumulative balances`(): Unit =
        runBlocking {
            val fund1 = GroupKey.ByFund(fundId.toString())
            val fund2 = GroupKey.ByFund("other-fund")
            whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), eq(unfilteredFilter), eq(GroupingCriteria.FUND)))
                .thenReturn(GroupedUnitAmounts(mapOf(
                    fund1 to UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("300.00"))),
                    fund2 to UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("200.00"))),
                )))
            whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), eq(unfilteredFilter), eq(GroupingCriteria.FUND)))
                .thenReturn(BucketedGroupedUnitAmounts(mapOf(
                    LocalDateTime.parse("2024-01-01T00:00:00") to GroupedUnitAmounts(mapOf(
                        fund1 to UnitAmounts(mapOf(Currency.RON to BigDecimal.parseString("100.00"))),
                    )),
                )))

            val report = resolve(listOf(Series.Balance), interval, groupBy = GroupingCriteria.FUND)
            val balance = report[Series.Balance]

            assertThat(balance.value("2024-01-01T00:00:00", fund1)).isEqualTo(BigDecimal.parseString("300.00"))
            assertThat(balance.value("2024-01-01T00:00:00", fund2)).isEqualTo(BigDecimal.parseString("200.00"))
            assertThat(balance.value("2024-02-01T00:00:00", fund1)).isEqualTo(BigDecimal.parseString("400.00"))
            assertThat(balance.value("2024-02-01T00:00:00", fund2)).isEqualTo(BigDecimal.parseString("200.00"))
        }
}
