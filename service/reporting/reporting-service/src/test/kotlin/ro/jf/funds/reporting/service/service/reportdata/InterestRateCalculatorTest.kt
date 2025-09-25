package ro.jf.funds.reporting.service.service.reportdata

import ch.obermuhlner.math.big.BigDecimalMath
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import ro.jf.funds.reporting.service.domain.InvestmentOpenPosition
import java.math.BigDecimal
import java.math.MathContext

class InterestRateCalculatorTest {
    private val MATH_CONTEXT = MathContext.DECIMAL64

    private val calculator = InterestRateCalculator()

    companion object {
        @JvmStatic
        fun getInterestRates(): List<BigDecimal> = listOf(
            BigDecimal("6.38"),
            BigDecimal("0.21"),
            BigDecimal("67.85"),
            BigDecimal("-54.43"),
            BigDecimal("-0.17")
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("getInterestRates")
    fun `given calculate interest rate`(interestRate: BigDecimal) {
        val growthFactor = interestRate.divide(100.toBigDecimal(), MATH_CONTEXT) + 1.toBigDecimal()
        val positions = listOf(
            InvestmentOpenPosition(LocalDate.parse("2023-01-01"), BigDecimal(45)),
            // 1 year 162 days
            InvestmentOpenPosition(LocalDate.parse("2023-07-23"), BigDecimal(50)),
            InvestmentOpenPosition(LocalDate.parse("2024-01-01"), BigDecimal(55))
        )
        val valuation = listOf(
            BigDecimal(45) * growthFactor.pow(BigDecimal(2)),
            BigDecimal(50) * growthFactor.pow((1.0 + 162.0 / 365.0).toBigDecimal()),
            BigDecimal(55) * growthFactor.pow(BigDecimal(1))
        ).sumOf { it }
        val valuationDate = LocalDate.parse("2025-01-01")
        val command = InterestRateCalculationCommand(
            positions = positions,
            valuation = valuation,
            valuationDate = valuationDate,
        )

        val calculatedRate = calculator.calculateInterestRate(command)

        assertThat(calculatedRate).isCloseTo(interestRate, within(BigDecimal("0.01")))
    }

    private fun BigDecimal.pow(exponent: BigDecimal): BigDecimal =
        BigDecimalMath.pow(this, exponent, MATH_CONTEXT)
}
