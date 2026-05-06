package ro.jf.funds.analytics.service.domain

import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class InterestRateCalculatorTest {
    private val calculator = InterestRateCalculator()

    @Test
    fun `given single position doubled in one year - when calculating interest rate - then returns approximately 100 percent`() {
        val result = calculator.calculateInterestRate(
            InterestRateCalculationCommand(
                positions = listOf(
                    InterestRateCalculationCommand.Position(LocalDate.parse("2023-01-01"), BigDecimal("1000"))
                ),
                valuation = BigDecimal("2000"),
                valuationDate = LocalDate.parse("2024-01-01"),
            )
        )
        assertThat(result.toDouble()).isCloseTo(100.0, Offset.offset(0.1))
    }

    @Test
    fun `given single position with 10 percent growth in one year - when calculating interest rate - then returns approximately 10 percent`() {
        val result = calculator.calculateInterestRate(
            InterestRateCalculationCommand(
                positions = listOf(
                    InterestRateCalculationCommand.Position(LocalDate.parse("2023-01-01"), BigDecimal("1000"))
                ),
                valuation = BigDecimal("1100"),
                valuationDate = LocalDate.parse("2024-01-01"),
            )
        )
        assertThat(result.toDouble()).isCloseTo(10.0, Offset.offset(0.1))
    }

    @Test
    fun `given multiple positions at different dates - when calculating interest rate - then accounts for time-weighted compounding`() {
        val result = calculator.calculateInterestRate(
            InterestRateCalculationCommand(
                positions = listOf(
                    InterestRateCalculationCommand.Position(LocalDate.parse("2023-01-01"), BigDecimal("1000")),
                    InterestRateCalculationCommand.Position(LocalDate.parse("2023-07-01"), BigDecimal("1000")),
                ),
                valuation = BigDecimal("2200"),
                valuationDate = LocalDate.parse("2024-01-01"),
            )
        )
        assertThat(result.toDouble()).isGreaterThan(5.0)
        assertThat(result.toDouble()).isLessThan(15.0)
    }

    @Test
    fun `given positions and interest rate - when calculating valuation - then returns expected value`() {
        val result = calculator.calculateValuation(
            ValuationCalculationCommand(
                positions = listOf(
                    InterestRateCalculationCommand.Position(LocalDate.parse("2023-01-01"), BigDecimal("1000"))
                ),
                valuationDate = LocalDate.parse("2024-01-01"),
                interestRate = BigDecimal("10"),
            )
        )
        assertThat(result.toDouble()).isCloseTo(1100.0, Offset.offset(1.0))
    }
}
