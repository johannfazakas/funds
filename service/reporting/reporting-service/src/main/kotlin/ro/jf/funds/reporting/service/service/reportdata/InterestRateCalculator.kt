package ro.jf.funds.reporting.service.service.reportdata

import ch.obermuhlner.math.big.BigDecimalMath.pow
import kotlinx.datetime.*
import ro.jf.funds.reporting.service.domain.InvestmentOpenPosition
import java.math.BigDecimal
import java.math.MathContext
import java.math.MathContext.DECIMAL64

private val YEARLY_DAYS = BigDecimal("365")
private val MINIMUM_GROWTH_FACTOR = BigDecimal("0.0001")

data class InterestRateCalculationCommand(
    val positions: List<InvestmentOpenPosition>,
    val valuation: BigDecimal,
    val valuationDate: LocalDate,
) {
    init {
        require(positions.isNotEmpty()) { "At least one position must be provided." }
        require(positions.all { it.date <= valuationDate }) { "Positions after valuation date provided." }
        require(positions.any { it.date < valuationDate }) { "No position before valuation date provided." }
        require(valuation > BigDecimal.ZERO) { "Valuation must be positive." }
    }
}

private fun convertRateToGrowthFactor(rate: BigDecimal, mathContext: MathContext): BigDecimal =
    rate.divide(100.toBigDecimal(), mathContext) + 1.toBigDecimal()

private fun convertGrowthFactorToRate(growthFactor: BigDecimal, mathContext: MathContext): BigDecimal =
    (growthFactor - 1.toBigDecimal()).multiply(100.toBigDecimal(), mathContext)

class InterestRateCalculator(
    private val initialProspectRate: BigDecimal = BigDecimal("10.0"),
    private val initialProspectRateStep: BigDecimal = BigDecimal("20.0"),
    private val prospectRateStepExponent: BigDecimal = BigDecimal("2.0"),
    private val valuationPrecision: BigDecimal = BigDecimal("0.001"),
    private val maxSteps: Int = 100,
    private val mathContext: MathContext = DECIMAL64,
) {
    private data class ProspectGrowthFactor(
        val factor: BigDecimal,
        val previousFactor: BigDecimal? = null,
        val upperBound: BigDecimal? = null,
        val lowerBound: BigDecimal? = null,
    ) {
        companion object {
            fun withInitialProspectRatio(rate: BigDecimal, mathContext: MathContext) =
                ProspectGrowthFactor(convertRateToGrowthFactor(rate, mathContext), rate)
        }
    }

    fun calculateInterestRate(command: InterestRateCalculationCommand): BigDecimal {
        val positionsWithRateExponent = associatePositionsWithRateExponent(command)
        val growthFactor = calculateGrowthFactor(positionsWithRateExponent, command.valuation)
        return convertGrowthFactorToRate(growthFactor, mathContext)
    }

    private fun associatePositionsWithRateExponent(command: InterestRateCalculationCommand): List<Pair<InvestmentOpenPosition, BigDecimal>> =
        command.positions.map { position ->
            val years = position.date.yearsUntil(command.valuationDate)
            val remainingDays = (position.date + DatePeriod(years)).daysUntil(command.valuationDate)
            val rateExponent = years.toBigDecimal() + remainingDays.toBigDecimal().divide(YEARLY_DAYS, mathContext)
            position to rateExponent
        }

    private tailrec fun calculateGrowthFactor(
        positionsWithRateExponent: List<Pair<InvestmentOpenPosition, BigDecimal>>,
        valuation: BigDecimal,
        prospectFactor: ProspectGrowthFactor =
            ProspectGrowthFactor.withInitialProspectRatio(initialProspectRate, mathContext),
        iteration: Int = 1,
    ): BigDecimal {
        if (iteration > maxSteps) return prospectFactor.factor

        val outcome = evaluateFactorOutcome(positionsWithRateExponent, prospectFactor.factor)
        if (outcomeMatchesValuation(outcome, valuation)) return prospectFactor.factor

        val nextProspectFactor = findNextProspectFactor(valuation, outcome, prospectFactor)
        return calculateGrowthFactor(positionsWithRateExponent, valuation, nextProspectFactor, iteration + 1)
    }

    private fun evaluateFactorOutcome(
        positionsWithRateExponent: List<Pair<InvestmentOpenPosition, BigDecimal>>,
        growthFactor: BigDecimal,
    ): BigDecimal =
        positionsWithRateExponent
            .sumOf { (position, rateExponent) ->
                val interest = pow(growthFactor, rateExponent, mathContext)
                position.amount.multiply(interest, mathContext)
            }

    private fun outcomeMatchesValuation(outcome: BigDecimal, valuation: BigDecimal) =
        outcome.minus(valuation).abs() <= valuationPrecision

    private fun findNextProspectFactor(
        valuation: BigDecimal,
        outcome: BigDecimal,
        prospectGrowthFactor: ProspectGrowthFactor,
    ): ProspectGrowthFactor {
        val (rate, previous, upperBound, lowerBound) = prospectGrowthFactor
        return if (valuation > outcome) { // next prospect rate should increase
            if (upperBound != null) {
                ProspectGrowthFactor(bisector(rate, upperBound), rate, upperBound, rate)
            } else {
                ProspectGrowthFactor(nextUnboundedUpperFactor(rate, previous), rate, null, rate)
            }
        } else { // next prospect rate should decrease
            if (lowerBound != null) {
                ProspectGrowthFactor(bisector(rate, lowerBound), rate, rate, lowerBound)
            } else {
                ProspectGrowthFactor(nextUnboundedLowerFactor(rate, previous), rate, rate, null)
            }
        }
    }

    private fun bisector(first: BigDecimal, second: BigDecimal): BigDecimal =
        (first + second).divide(BigDecimal(2), mathContext)

    private fun nextUnboundedUpperFactor(factor: BigDecimal, previousRate: BigDecimal?): BigDecimal =
        factor + nextUnboundedFactorStep(factor, previousRate)

    private fun nextUnboundedLowerFactor(factor: BigDecimal, previousRate: BigDecimal?): BigDecimal =
        maxOf(factor - nextUnboundedFactorStep(factor, previousRate), MINIMUM_GROWTH_FACTOR)

    private fun nextUnboundedFactorStep(currentFactor: BigDecimal, previousFactor: BigDecimal?): BigDecimal =
        if (previousFactor == null)
            convertRateToGrowthFactor(initialProspectRateStep, mathContext)
        else {
            (currentFactor - previousFactor).abs().multiply(prospectRateStepExponent)
        }
}
