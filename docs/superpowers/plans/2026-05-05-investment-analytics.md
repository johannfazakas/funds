# Investment Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate performance and interest rate reports into the analytics service with unified generic response model and web client support.

**Architecture:** Extend the existing analytics service with two new report endpoints (performance, interest-rate) that reuse the materialized `analytics_record` table and existing aggregate query patterns. Unify all response models under a generic `AnalyticsReportTO<T>`. Add a metric selector to the web client's analytics page.

**Tech Stack:** Kotlin/Ktor, Exposed ORM, PostgreSQL, Kafka, kotlinx.serialization, Recharts (web), TypeScript/React

---

## File Map

### analytics-api (KMP commonMain)
- Modify: `service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/GroupingCriteria.kt` — rename CURRENCY to FINANCIAL_UNIT
- Modify: `service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/AnalyticsReportTO.kt` — add type parameter `<T>`, existing becomes `<BigDecimal>`
- Create: `service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/PerformanceDataTO.kt`
- Create: `service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/InterestRateDataTO.kt`

### analytics-service (JVM)
- Modify: `service/analytics/analytics-service/build.gradle.kts` — add `libs.big.math` dependency
- Modify: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/domain/AnalyticsRecordFilter.kt` — add transactionTypes field
- Modify: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/persistence/AnalyticsRecordRepository.kt` — add transactionTypes to filter, add getRecords/getRecordsBefore methods
- Modify: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/service/AnalyticsService.kt` — update return types to `AnalyticsReportTO<BigDecimal>`
- Create: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/domain/InterestRateCalculator.kt`
- Create: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/service/PerformanceService.kt`
- Create: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/service/InterestRateService.kt`
- Modify: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/web/AnalyticsApiRouting.kt` — add performance and interest-rate routes
- Modify: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/config/AnalyticsDependencies.kt` — register new services
- Modify: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/config/AnalyticsRouting.kt` — wire new routes

### analytics-service tests
- Create: `service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/service/PerformanceServiceTest.kt`
- Create: `service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/service/InterestRateServiceTest.kt`
- Create: `service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/domain/InterestRateCalculatorTest.kt`
- Modify: `service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/web/AnalyticsApiTest.kt` — update generic type usage, add performance/interest-rate integration tests

### web client
- Modify: `client/web-client/src/jsMain/resources/react/api/analyticsApi.ts` — add types, rename GroupBy value, add fetch functions
- Modify: `client/web-client/src/jsMain/resources/react/pages/AnalyticsPage.tsx` — add report types, metric selector, handle new responses

---

### Task 1: Generic Response Model

**Files:**
- Modify: `service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/AnalyticsReportTO.kt`
- Modify: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/service/AnalyticsService.kt`
- Modify: `service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/web/AnalyticsApiTest.kt`
- Modify: `service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/service/AnalyticsServiceTest.kt`

- [ ] **Step 1: Update AnalyticsReportTO with generic type parameter**

```kotlin
// service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/AnalyticsReportTO.kt
package ro.jf.funds.analytics.api.model

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.serialization.BigDecimalSerializer

@Serializable
data class AnalyticsReportTO<T>(
    val granularity: TimeGranularity,
    val buckets: List<AnalyticsBucketTO<T>>,
)

@Serializable
data class AnalyticsBucketTO<T>(
    val dateTime: LocalDateTime,
    val groups: List<AnalyticsGroupBucketTO<T>>,
)

@Serializable
data class AnalyticsGroupBucketTO<T>(
    val groupKey: String? = null,
    val value: T,
)
```

- [ ] **Step 2: Update AnalyticsService return types to use explicit generic**

In `AnalyticsService.kt`, change the return types of `getBalanceReport` and `getNetChangeReport` from `AnalyticsReportTO` to `AnalyticsReportTO<BigDecimal>`. Update the bucket construction to use the new generic types:

```kotlin
// Change signatures:
suspend fun getBalanceReport(...): AnalyticsReportTO<BigDecimal>
suspend fun getNetChangeReport(...): AnalyticsReportTO<BigDecimal>
```

Replace all `AnalyticsBucketTO(` with `AnalyticsBucketTO<BigDecimal>(` and all `AnalyticsGroupBucketTO(` with `AnalyticsGroupBucketTO<BigDecimal>(` in the service methods.

- [ ] **Step 3: Update test files for generic types**

In `AnalyticsApiTest.kt`, change `response.body<AnalyticsReportTO>()` to `response.body<AnalyticsReportTO<BigDecimal>>()`.

In `AnalyticsServiceTest.kt`, update assertions that reference `report.buckets[*].groups[*].value` — these remain unchanged as `BigDecimal` is still the value type.

- [ ] **Step 4: Run tests to verify no regressions**

Run: `./gradlew :service:analytics:analytics-service:test`
Expected: All existing tests pass.

- [ ] **Step 5: Commit**

```bash
git add service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/AnalyticsReportTO.kt
git add service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/service/AnalyticsService.kt
git add service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/web/AnalyticsApiTest.kt
git add service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/service/AnalyticsServiceTest.kt
git commit -m "refactor: make AnalyticsReportTO generic with type parameter"
```

---

### Task 2: Rename CURRENCY to FINANCIAL_UNIT in GroupingCriteria

**Files:**
- Modify: `service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/GroupingCriteria.kt`
- Modify: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/persistence/AnalyticsRecordRepository.kt`
- Modify: `service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/service/AnalyticsServiceTest.kt`

- [ ] **Step 1: Rename enum value**

```kotlin
// service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/GroupingCriteria.kt
package ro.jf.funds.analytics.api.model

import kotlinx.serialization.Serializable

@Serializable
enum class GroupingCriteria {
    FINANCIAL_UNIT,
    ACCOUNT,
    FUND,
    CATEGORY,
}
```

- [ ] **Step 2: Update repository mapping**

In `AnalyticsRecordRepository.kt`, update the two `when` expressions that reference `GroupingCriteria.CURRENCY`:

```kotlin
private fun GroupingCriteria.toColumn(): Column<*> = when (this) {
    GroupingCriteria.FINANCIAL_UNIT -> AnalyticsRecordTable.unit
    GroupingCriteria.ACCOUNT -> AnalyticsRecordTable.accountId
    GroupingCriteria.FUND -> AnalyticsRecordTable.fundId
    GroupingCriteria.CATEGORY -> AnalyticsRecordTable.category
}

private fun ResultRow.extractGroupKey(groupBy: GroupingCriteria): String? = when (groupBy) {
    GroupingCriteria.FINANCIAL_UNIT -> this[AnalyticsRecordTable.unit].value
    GroupingCriteria.ACCOUNT -> this[AnalyticsRecordTable.accountId].toString()
    GroupingCriteria.FUND -> this[AnalyticsRecordTable.fundId].toString()
    GroupingCriteria.CATEGORY -> this[AnalyticsRecordTable.category]
}
```

- [ ] **Step 3: Update test references**

In `AnalyticsServiceTest.kt`, replace any `GroupingCriteria.CURRENCY` references with `GroupingCriteria.FINANCIAL_UNIT`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :service:analytics:analytics-service:test`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/GroupingCriteria.kt
git add service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/persistence/AnalyticsRecordRepository.kt
git add service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/service/AnalyticsServiceTest.kt
git commit -m "refactor: rename CURRENCY to FINANCIAL_UNIT in GroupingCriteria"
```

---

### Task 3: Add TransactionType Filter to AnalyticsRecordFilter and Repository

**Files:**
- Modify: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/domain/AnalyticsRecordFilter.kt`
- Modify: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/persistence/AnalyticsRecordRepository.kt`
- Modify: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/web/AnalyticsApiRouting.kt`

- [ ] **Step 1: Add transactionTypes to AnalyticsRecordFilter**

```kotlin
// service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/domain/AnalyticsRecordFilter.kt
package ro.jf.funds.analytics.service.domain

import com.benasher44.uuid.Uuid
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.FinancialUnit

data class AnalyticsRecordFilter(
    val fundIds: List<Uuid> = emptyList(),
    val units: List<FinancialUnit> = emptyList(),
    val transactionTypes: List<TransactionType> = emptyList(),
)
```

- [ ] **Step 2: Apply transactionTypes filter in repository Query.applyFilter**

In `AnalyticsRecordRepository.kt`, extend the `applyFilter` function:

```kotlin
private fun Query.applyFilter(filter: AnalyticsRecordFilter): Query = this
    .let { query ->
        if (filter.fundIds.isNotEmpty())
            query.andWhere { AnalyticsRecordTable.fundId inList filter.fundIds.map { it.toJavaUuid() } }
        else query
    }
    .let { query ->
        if (filter.units.isNotEmpty())
            query.andWhere {
                filter.units.map<FinancialUnit, Op<Boolean>> {
                    AnalyticsRecordTable.unit.contains(Json.encodeToString(FinancialUnit.serializer(), it))
                }.reduce { acc, op -> acc or op }
            }
        else query
    }
    .let { query ->
        if (filter.transactionTypes.isNotEmpty())
            query.andWhere { AnalyticsRecordTable.transactionType inList filter.transactionTypes.map { it.name } }
        else query
    }
```

- [ ] **Step 3: Add getRecords and getRecordsBefore methods to repository**

Append to `AnalyticsRecordRepository.kt`:

```kotlin
suspend fun getRecords(
    userId: Uuid,
    interval: ReportInterval,
    filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
): List<AnalyticsRecord> = blockingTransaction {
    AnalyticsRecordTable
        .selectAll()
        .where { AnalyticsRecordTable.userId eq userId.toJavaUuid() }
        .andWhere { AnalyticsRecordTable.dateTime greaterEq interval.from.toJavaLocalDateTime() }
        .andWhere { AnalyticsRecordTable.dateTime lessEq interval.to.toJavaLocalDateTime() }
        .applyFilter(filter)
        .orderBy(AnalyticsRecordTable.dateTime)
        .map { it.toAnalyticsRecord() }
}

suspend fun getRecordsBefore(
    userId: Uuid,
    before: LocalDateTime,
    filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
): List<AnalyticsRecord> = blockingTransaction {
    AnalyticsRecordTable
        .selectAll()
        .where { AnalyticsRecordTable.userId eq userId.toJavaUuid() }
        .andWhere { AnalyticsRecordTable.dateTime less before.toJavaLocalDateTime() }
        .applyFilter(filter)
        .orderBy(AnalyticsRecordTable.dateTime)
        .map { it.toAnalyticsRecord() }
}

private fun ResultRow.toAnalyticsRecord() = AnalyticsRecord(
    id = com.benasher44.uuid.Uuid.fromString(this[AnalyticsRecordTable.id].toString()),
    userId = com.benasher44.uuid.Uuid.fromString(this[AnalyticsRecordTable.userId].toString()),
    fundId = com.benasher44.uuid.Uuid.fromString(this[AnalyticsRecordTable.fundId].toString()),
    accountId = com.benasher44.uuid.Uuid.fromString(this[AnalyticsRecordTable.accountId].toString()),
    transactionId = com.benasher44.uuid.Uuid.fromString(this[AnalyticsRecordTable.transactionId].toString()),
    transactionType = TransactionType.valueOf(this[AnalyticsRecordTable.transactionType]),
    dateTime = this[AnalyticsRecordTable.dateTime].toKotlinLocalDateTime(),
    amount = this[AnalyticsRecordTable.amount],
    unit = this[AnalyticsRecordTable.unit],
    category = this[AnalyticsRecordTable.category]?.let { ro.jf.funds.platform.api.model.Category(it) },
)
```

- [ ] **Step 4: Run tests to confirm no regressions**

Run: `./gradlew :service:analytics:analytics-service:test`
Expected: All existing tests pass (transactionTypes defaults to empty = no filter applied).

- [ ] **Step 5: Commit**

```bash
git add service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/domain/AnalyticsRecordFilter.kt
git add service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/persistence/AnalyticsRecordRepository.kt
git commit -m "feat: add transactionTypes filter and raw record queries to analytics repository"
```

---

### Task 4: InterestRateCalculator

**Files:**
- Modify: `service/analytics/analytics-service/build.gradle.kts`
- Create: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/domain/InterestRateCalculator.kt`
- Create: `service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/domain/InterestRateCalculatorTest.kt`

- [ ] **Step 1: Add big-math dependency**

In `service/analytics/analytics-service/build.gradle.kts`, add to dependencies:

```kotlin
dependencies {
    implementation(project(":service:analytics:analytics-api"))
    implementation(project(":service:fund:fund-api"))
    implementation(project(":service:conversion:conversion-sdk"))
    implementation(project(":platform:platform-jvm"))
    implementation(libs.big.math)
    testImplementation(project(":platform:platform-jvm-test"))
}
```

- [ ] **Step 2: Write failing test for InterestRateCalculator**

```kotlin
// service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/domain/InterestRateCalculatorTest.kt
package ro.jf.funds.analytics.service.domain

import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
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
        assertThat(result.toDouble()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.1))
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
        assertThat(result.toDouble()).isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.1))
    }

    @Test
    fun `given multiple positions - when calculating interest rate - then accounts for time-weighted compounding`() {
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
        assertThat(result.toDouble()).isCloseTo(1100.0, org.assertj.core.data.Offset.offset(1.0))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :service:analytics:analytics-service:test --tests "*.InterestRateCalculatorTest"`
Expected: FAIL — class not found.

- [ ] **Step 4: Implement InterestRateCalculator**

Copy and adapt from `service/reporting/reporting-service/src/main/kotlin/ro/jf/funds/reporting/service/service/reportdata/InterestRateCalculator.kt`:

```kotlin
// service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/domain/InterestRateCalculator.kt
package ro.jf.funds.analytics.service.domain

import ch.obermuhlner.math.big.BigDecimalMath.pow
import kotlinx.datetime.*
import java.math.BigDecimal
import java.math.MathContext
import java.math.MathContext.DECIMAL64

private val YEARLY_DAYS = BigDecimal("365")
private val MINIMUM_GROWTH_FACTOR = BigDecimal("0.0001")

data class InterestRateCalculationCommand(
    val positions: List<Position>,
    val valuation: BigDecimal,
    val valuationDate: LocalDate,
) {
    init {
        require(positions.isNotEmpty()) { "At least one position must be provided." }
        require(positions.all { it.date <= valuationDate }) { "Positions after valuation date provided." }
        require(positions.any { it.date < valuationDate }) { "No position before valuation date provided." }
        require(valuation > BigDecimal.ZERO) { "Valuation must be positive." }
    }

    data class Position(
        val date: LocalDate,
        val amount: BigDecimal,
    )
}

data class ValuationCalculationCommand(
    val positions: List<InterestRateCalculationCommand.Position>,
    val valuationDate: LocalDate,
    val interestRate: BigDecimal,
) {
    init {
        require(positions.isNotEmpty()) { "At least one position must be provided." }
        require(positions.all { it.date <= valuationDate }) { "Positions after valuation date provided." }
        require(positions.any { it.date < valuationDate }) { "No position before valuation date provided." }
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

    fun calculateValuation(command: ValuationCalculationCommand): BigDecimal {
        val growthFactor = convertRateToGrowthFactor(command.interestRate, mathContext)
        val positionsWithRateExponent = command.positions.map { position ->
            val years = position.date.yearsUntil(command.valuationDate)
            val remainingDays = (position.date + DatePeriod(years)).daysUntil(command.valuationDate)
            val rateExponent = years.toBigDecimal() + remainingDays.toBigDecimal().divide(YEARLY_DAYS, mathContext)
            position to rateExponent
        }
        return evaluateFactorOutcome(positionsWithRateExponent, growthFactor)
    }

    private fun associatePositionsWithRateExponent(command: InterestRateCalculationCommand): List<Pair<InterestRateCalculationCommand.Position, BigDecimal>> =
        command.positions.map { position ->
            val years = position.date.yearsUntil(command.valuationDate)
            val remainingDays = (position.date + DatePeriod(years)).daysUntil(command.valuationDate)
            val rateExponent = years.toBigDecimal() + remainingDays.toBigDecimal().divide(YEARLY_DAYS, mathContext)
            position to rateExponent
        }

    private tailrec fun calculateGrowthFactor(
        positionsWithRateExponent: List<Pair<InterestRateCalculationCommand.Position, BigDecimal>>,
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
        positionsWithRateExponent: List<Pair<InterestRateCalculationCommand.Position, BigDecimal>>,
        growthFactor: BigDecimal,
    ): BigDecimal =
        positionsWithRateExponent.sumOf { (position, rateExponent) ->
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
        return if (valuation > outcome) {
            if (upperBound != null) {
                ProspectGrowthFactor(bisector(rate, upperBound), rate, upperBound, rate)
            } else {
                ProspectGrowthFactor(nextUnboundedUpperFactor(rate, previous), rate, null, rate)
            }
        } else {
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
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :service:analytics:analytics-service:test --tests "*.InterestRateCalculatorTest"`
Expected: All 4 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add service/analytics/analytics-service/build.gradle.kts
git add service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/domain/InterestRateCalculator.kt
git add service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/domain/InterestRateCalculatorTest.kt
git commit -m "feat: add InterestRateCalculator to analytics service domain"
```

---

### Task 5: Performance API Model

**Files:**
- Create: `service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/PerformanceDataTO.kt`

- [ ] **Step 1: Create PerformanceDataTO**

```kotlin
// service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/PerformanceDataTO.kt
package ro.jf.funds.analytics.api.model

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.serialization.BigDecimalSerializer

@Serializable
data class PerformanceDataTO(
    @Serializable(with = BigDecimalSerializer::class)
    val totalInvestment: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val currentInvestment: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val totalProfit: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val currentProfit: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val totalInstrumentValue: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val currencyValue: BigDecimal,
)
```

- [ ] **Step 2: Commit**

```bash
git add service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/PerformanceDataTO.kt
git commit -m "feat: add PerformanceDataTO API model"
```

---

### Task 6: InterestRate API Model

**Files:**
- Create: `service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/InterestRateDataTO.kt`

- [ ] **Step 1: Create InterestRateDataTO**

```kotlin
// service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/InterestRateDataTO.kt
package ro.jf.funds.analytics.api.model

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.serialization.BigDecimalSerializer

@Serializable
data class InterestRateDataTO(
    @Serializable(with = BigDecimalSerializer::class)
    val totalInterestRate: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val currentInterestRate: BigDecimal,
)
```

- [ ] **Step 2: Commit**

```bash
git add service/analytics/analytics-api/src/commonMain/kotlin/ro/jf/funds/analytics/api/model/InterestRateDataTO.kt
git commit -m "feat: add InterestRateDataTO API model"
```

---

### Task 7: PerformanceService

**Files:**
- Create: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/service/PerformanceService.kt`
- Create: `service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/service/PerformanceServiceTest.kt`

- [ ] **Step 1: Write failing test for ungrouped performance report**

```kotlin
// service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/service/PerformanceServiceTest.kt
package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.uuid4
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.domain.*
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Instrument

class PerformanceServiceTest {
    private val analyticsRecordRepository = mock<AnalyticsRecordRepository>()
    private val conversionSdk = mock<ConversionSdk>()
    private val service = PerformanceService(analyticsRecordRepository, conversionSdk)

    private val userId = uuid4()
    private val interval = ReportInterval(
        granularity = TimeGranularity.MONTHLY,
        from = LocalDateTime.parse("2024-01-01T00:00:00"),
        to = LocalDateTime.parse("2024-03-01T00:00:00"),
    )

    private val mockRates: MutableMap<Triple<FinancialUnit, Currency, LocalDate>, BigDecimal> = mutableMapOf()

    @BeforeEach
    fun setupConversionSdkMock(): Unit = runBlocking {
        mockRates.clear()
        whenever(conversionSdk.convert(any())).thenAnswer { invocation ->
            val request = invocation.arguments[0] as ConversionsRequest
            ConversionsResponse(request.conversions.map { req ->
                val rate = if (req.sourceUnit == req.targetCurrency) BigDecimal.ONE
                else mockRates[Triple(req.sourceUnit, req.targetCurrency, req.date)]
                    ?: error("No mock rate for $req")
                ConversionResponse(req.sourceUnit, req.targetCurrency, req.date, rate)
            })
        }
    }

    @Test
    fun `given open position records - when getting performance report - then returns investment and profit`(): Unit = runBlocking {
        val eur = Currency("EUR")
        val sxr8 = Instrument("SXR8")

        // Previous: bought 2 units of SXR8 for 200 EUR each
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any()))
            .thenReturn(UnitAmounts.EMPTY)
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any()))
            .thenReturn(BucketedUnitAmounts(emptyMap()))

        // Investment filter: OpenPosition currency records
        val investmentFilter = AnalyticsRecordFilter(transactionTypes = listOf(ro.jf.funds.fund.api.model.TransactionType.OPEN_POSITION), units = listOf(eur))
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), org.mockito.kotlin.eq(investmentFilter)))
            .thenReturn(UnitAmounts(mapOf(eur to BigDecimal.parseString("-400.00"))))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), org.mockito.kotlin.eq(investmentFilter)))
            .thenReturn(BucketedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to UnitAmounts(mapOf(eur to BigDecimal.parseString("-200.00"))),
            )))

        // Instrument filter: OpenPosition + ClosePosition instrument records
        val instrumentFilter = AnalyticsRecordFilter(
            transactionTypes = listOf(ro.jf.funds.fund.api.model.TransactionType.OPEN_POSITION, ro.jf.funds.fund.api.model.TransactionType.CLOSE_POSITION),
            units = listOf(sxr8),
        )
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), org.mockito.kotlin.eq(instrumentFilter)))
            .thenReturn(UnitAmounts(mapOf(sxr8 to BigDecimal.parseString("2.00"))))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), org.mockito.kotlin.eq(instrumentFilter)))
            .thenReturn(BucketedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to UnitAmounts(mapOf(sxr8 to BigDecimal.parseString("1.00"))),
            )))

        // Currency value: all currency records in fund
        val currencyFilter = AnalyticsRecordFilter(units = listOf(eur))
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), org.mockito.kotlin.eq(currencyFilter)))
            .thenReturn(UnitAmounts(mapOf(eur to BigDecimal.parseString("100.00"))))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), org.mockito.kotlin.eq(currencyFilter)))
            .thenReturn(BucketedUnitAmounts(mapOf(
                LocalDateTime.parse("2024-01-01T00:00:00") to UnitAmounts(mapOf(eur to BigDecimal.parseString("-200.00"))),
            )))

        // SXR8 at 250 EUR in Jan, 260 EUR in Feb
        mockRates[Triple(sxr8, eur, LocalDate.parse("2024-01-01"))] = BigDecimal.parseString("250.00")
        mockRates[Triple(sxr8, eur, LocalDate.parse("2024-02-01"))] = BigDecimal.parseString("260.00")
        mockRates[Triple(eur, eur, LocalDate.parse("2024-01-01"))] = BigDecimal.ONE
        mockRates[Triple(eur, eur, LocalDate.parse("2024-02-01"))] = BigDecimal.ONE

        val report = service.getPerformanceReport(userId, interval, AnalyticsRecordFilter(), eur)

        assertThat(report.granularity).isEqualTo(TimeGranularity.MONTHLY)
        assertThat(report.buckets).hasSize(2)
        // Bucket 1 (Jan): prev 2 units @ 250 = 500 instrument value, 100 currency, investment = 400
        assertThat(report.buckets[0].groups[0].value.totalInstrumentValue).isEqualTo(BigDecimal.parseString("500.00"))
        assertThat(report.buckets[0].groups[0].value.currencyValue).isEqualTo(BigDecimal.parseString("100.00"))
        assertThat(report.buckets[0].groups[0].value.totalInvestment).isEqualTo(BigDecimal.parseString("400.00"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :service:analytics:analytics-service:test --tests "*.PerformanceServiceTest"`
Expected: FAIL — PerformanceService class not found.

- [ ] **Step 3: Implement PerformanceService**

```kotlin
// service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/service/PerformanceService.kt
package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.*
import ro.jf.funds.analytics.service.domain.AnalyticsRecordFilter
import ro.jf.funds.analytics.service.domain.ReportInterval
import ro.jf.funds.analytics.service.domain.UnitAmounts
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionRequest
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.UnitType

private val log = logger { }

class PerformanceService(
    private val analyticsRecordRepository: AnalyticsRecordRepository,
    private val conversionSdk: ConversionSdk,
) {
    suspend fun getPerformanceReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
        targetCurrency: Currency,
        groupBy: GroupingCriteria? = null,
    ): AnalyticsReportTO<PerformanceDataTO> {
        log.info { "Generating performance report for user $userId, interval=$interval, targetCurrency=$targetCurrency, groupBy=$groupBy" }
        return getUngroupedPerformanceReport(userId, interval, filter, targetCurrency)
    }

    private data class PerformanceState(
        val totalInvestment: UnitAmounts,
        val instrumentUnits: UnitAmounts,
        val currencyAmounts: UnitAmounts,
        val previousTotalProfit: BigDecimal,
    )

    private suspend fun getUngroupedPerformanceReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter,
        targetCurrency: Currency,
    ): AnalyticsReportTO<PerformanceDataTO> {
        val investmentFilter = filter.copy(
            transactionTypes = listOf(TransactionType.OPEN_POSITION),
            units = filter.units.ifEmpty { null }?.filter { it.type == UnitType.CURRENCY } ?: emptyList(),
        )
        val instrumentFilter = filter.copy(
            transactionTypes = listOf(TransactionType.OPEN_POSITION, TransactionType.CLOSE_POSITION),
            units = filter.units.ifEmpty { null }?.filter { it.type == UnitType.INSTRUMENT } ?: emptyList(),
        )
        val currencyFilter = filter.copy(
            units = filter.units.ifEmpty { null }?.filter { it.type == UnitType.CURRENCY } ?: emptyList(),
        )

        val prevInvestment = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, investmentFilter)
        val prevInstruments = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, instrumentFilter)
        val prevCurrency = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, currencyFilter)

        val bucketedInvestment = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, investmentFilter)
        val bucketedInstruments = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, instrumentFilter)
        val bucketedCurrency = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, currencyFilter)

        val initialState = PerformanceState(
            totalInvestment = prevInvestment,
            instrumentUnits = prevInstruments,
            currencyAmounts = prevCurrency,
            previousTotalProfit = BigDecimal.ZERO,
        )

        val buckets = interval.generateBucketedData(initialState) { dateTime, state ->
            val currentBucketInvestment = bucketedInvestment.getBucket(dateTime)
            val currentBucketInstruments = bucketedInstruments.getBucket(dateTime)
            val currentBucketCurrency = bucketedCurrency.getBucket(dateTime)

            val totalInvestmentAmounts = state.totalInvestment + currentBucketInvestment
            val totalInstrumentUnits = state.instrumentUnits + currentBucketInstruments
            val totalCurrencyAmounts = state.currencyAmounts + currentBucketCurrency

            val date = dateTime.date
            val instrumentValue = convert(totalInstrumentUnits, targetCurrency, date)
            val currencyValue = convert(totalCurrencyAmounts, targetCurrency, date)
            val totalInvestment = convert(totalInvestmentAmounts, targetCurrency, date).negate()
            val currentInvestmentConverted = convert(currentBucketInvestment, targetCurrency, date).negate()

            val totalProfit = instrumentValue - totalInvestment
            val currentProfit = totalProfit - state.previousTotalProfit

            val data = PerformanceDataTO(
                totalInvestment = totalInvestment,
                currentInvestment = currentInvestmentConverted,
                totalProfit = totalProfit,
                currentProfit = currentProfit,
                totalInstrumentValue = instrumentValue,
                currencyValue = currencyValue,
            )

            val nextState = PerformanceState(
                totalInvestment = totalInvestmentAmounts,
                instrumentUnits = totalInstrumentUnits,
                currencyAmounts = totalCurrencyAmounts,
                previousTotalProfit = totalProfit,
            )

            AnalyticsBucketTO(dateTime, listOf(AnalyticsGroupBucketTO(value = data))) to nextState
        }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
    }

    private suspend fun convert(amounts: UnitAmounts, targetCurrency: Currency, date: LocalDate): BigDecimal {
        if (amounts.units.isEmpty()) return BigDecimal.ZERO
        val request = ConversionsRequest(amounts.units.map { ConversionRequest(it, targetCurrency, date) })
        val rates = conversionSdk.convert(request)
        return amounts.entries.fold(BigDecimal.ZERO) { acc, (unit, amount) ->
            val rate = rates.getRate(unit, targetCurrency, date)
            if (rate == null) {
                log.warn { "Conversion rate not found for $unit -> $targetCurrency on $date" }
                return@fold acc
            }
            acc + amount * rate
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :service:analytics:analytics-service:test --tests "*.PerformanceServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/service/PerformanceService.kt
git add service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/service/PerformanceServiceTest.kt
git commit -m "feat: add PerformanceService with ungrouped performance report"
```

---

### Task 8: InterestRateService

**Files:**
- Create: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/service/InterestRateService.kt`
- Create: `service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/service/InterestRateServiceTest.kt`

- [ ] **Step 1: Write failing test for interest rate report**

```kotlin
// service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/service/InterestRateServiceTest.kt
package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.uuid4
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.domain.*
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Instrument

class InterestRateServiceTest {
    private val analyticsRecordRepository = mock<AnalyticsRecordRepository>()
    private val conversionSdk = mock<ConversionSdk>()
    private val interestRateCalculator = InterestRateCalculator()
    private val service = InterestRateService(analyticsRecordRepository, conversionSdk, interestRateCalculator)

    private val userId = uuid4()
    private val fundId = uuid4()
    private val accountId = uuid4()
    private val interval = ReportInterval(
        granularity = TimeGranularity.YEARLY,
        from = LocalDateTime.parse("2024-01-01T00:00:00"),
        to = LocalDateTime.parse("2025-01-01T00:00:00"),
    )

    private val mockRates: MutableMap<Triple<FinancialUnit, Currency, LocalDate>, BigDecimal> = mutableMapOf()

    @BeforeEach
    fun setupConversionSdkMock(): Unit = runBlocking {
        mockRates.clear()
        whenever(conversionSdk.convert(any())).thenAnswer { invocation ->
            val request = invocation.arguments[0] as ConversionsRequest
            ConversionsResponse(request.conversions.map { req ->
                val rate = if (req.sourceUnit == req.targetCurrency) BigDecimal.ONE
                else mockRates[Triple(req.sourceUnit, req.targetCurrency, req.date)]
                    ?: error("No mock rate for $req")
                ConversionResponse(req.sourceUnit, req.targetCurrency, req.date, rate)
            })
        }
    }

    @Test
    fun `given investment with 10 percent growth - when getting interest rate report - then returns approximately 10 percent`(): Unit = runBlocking {
        val eur = Currency("EUR")
        val sxr8 = Instrument("SXR8")

        // Previous position: bought 10 units at 100 EUR on 2023-01-01
        whenever(analyticsRecordRepository.getRecordsBefore(any(), any(), any()))
            .thenReturn(listOf(
                AnalyticsRecord(
                    id = uuid4(), userId = userId, fundId = fundId, accountId = accountId,
                    transactionId = uuid4(), transactionType = TransactionType.OPEN_POSITION,
                    dateTime = LocalDateTime.parse("2023-01-01T10:00:00"),
                    amount = java.math.BigDecimal("-1000.00").let { com.ionspin.kotlin.bignum.decimal.BigDecimal.parseString(it.toPlainString()) },
                    unit = eur, category = null,
                )
            ))

        // Instrument units for valuation
        whenever(analyticsRecordRepository.getUnitAmountsBefore(any(), any(), any()))
            .thenReturn(UnitAmounts(mapOf(sxr8 to BigDecimal.parseString("10.00"))))
        whenever(analyticsRecordRepository.getBucketedUnitAmounts(any(), any(), any()))
            .thenReturn(BucketedUnitAmounts(emptyMap()))
        whenever(analyticsRecordRepository.getRecords(any(), any(), any()))
            .thenReturn(emptyList())

        // SXR8 valued at 110 EUR at end of 2024 (10% growth over 1 year from position date)
        mockRates[Triple(sxr8, eur, LocalDate.parse("2024-01-01"))] = BigDecimal.parseString("110.00")

        val report = service.getInterestRateReport(userId, interval, AnalyticsRecordFilter(), eur)

        assertThat(report.granularity).isEqualTo(TimeGranularity.YEARLY)
        assertThat(report.buckets).hasSize(1)
        assertThat(report.buckets[0].groups[0].value.totalInterestRate.doubleValue(false))
            .isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.5))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :service:analytics:analytics-service:test --tests "*.InterestRateServiceTest"`
Expected: FAIL — InterestRateService class not found.

- [ ] **Step 3: Implement InterestRateService**

```kotlin
// service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/service/InterestRateService.kt
package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.Uuid
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.LocalDate
import mu.KotlinLogging.logger
import ro.jf.funds.analytics.api.model.*
import ro.jf.funds.analytics.service.domain.*
import ro.jf.funds.analytics.service.persistence.AnalyticsRecordRepository
import ro.jf.funds.conversion.api.model.ConversionRequest
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.sdk.ConversionSdk
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.UnitType

private val log = logger { }

class InterestRateService(
    private val analyticsRecordRepository: AnalyticsRecordRepository,
    private val conversionSdk: ConversionSdk,
    private val interestRateCalculator: InterestRateCalculator,
) {
    suspend fun getInterestRateReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter = AnalyticsRecordFilter(),
        targetCurrency: Currency,
        groupBy: GroupingCriteria? = null,
    ): AnalyticsReportTO<InterestRateDataTO> {
        log.info { "Generating interest rate report for user $userId, interval=$interval, targetCurrency=$targetCurrency" }
        return getUngroupedInterestRateReport(userId, interval, filter, targetCurrency)
    }

    private data class InterestRateState(
        val allPositions: List<InterestRateCalculationCommand.Position>,
        val instrumentUnits: UnitAmounts,
        val previousValuation: BigDecimal,
        val previousValuationDate: LocalDate,
    )

    private suspend fun getUngroupedInterestRateReport(
        userId: Uuid,
        interval: ReportInterval,
        filter: AnalyticsRecordFilter,
        targetCurrency: Currency,
    ): AnalyticsReportTO<InterestRateDataTO> {
        val positionFilter = filter.copy(transactionTypes = listOf(TransactionType.OPEN_POSITION))
        val instrumentFilter = filter.copy(
            transactionTypes = listOf(TransactionType.OPEN_POSITION, TransactionType.CLOSE_POSITION),
            units = filter.units.filter { it.type == UnitType.INSTRUMENT },
        )

        val previousPositionRecords = analyticsRecordRepository.getRecordsBefore(userId, interval.from, positionFilter)
        val previousPositions = previousPositionRecords.toPositions(targetCurrency)

        val prevInstrumentUnits = analyticsRecordRepository.getUnitAmountsBefore(userId, interval.from, instrumentFilter)
        val bucketedInstrumentUnits = analyticsRecordRepository.getBucketedUnitAmounts(userId, interval, instrumentFilter)

        val bucketPositionRecords = analyticsRecordRepository.getRecords(userId, interval, positionFilter)

        val prevValuationDate = interval.from.date
        val prevValuation = convert(prevInstrumentUnits, targetCurrency, prevValuationDate)

        val initialState = InterestRateState(
            allPositions = previousPositions,
            instrumentUnits = prevInstrumentUnits,
            previousValuation = prevValuation,
            previousValuationDate = prevValuationDate,
        )

        val buckets = interval.generateBucketedData(initialState) { dateTime, state ->
            val bucketInstruments = bucketedInstrumentUnits.getBucket(dateTime)
            val totalInstrumentUnits = state.instrumentUnits + bucketInstruments

            val valuationDate = dateTime.date
            val valuation = convert(totalInstrumentUnits, targetCurrency, valuationDate)

            val currentBucketRecords = bucketPositionRecords.filter { record ->
                val bucketStart = dateTime
                record.dateTime >= bucketStart
            }
            val currentPositions = currentBucketRecords
                .filter { it.unit.type == UnitType.CURRENCY }
                .map { record ->
                    val rate = getRate(record.unit, targetCurrency, record.dateTime.date)
                    InterestRateCalculationCommand.Position(
                        date = record.dateTime.date,
                        amount = record.amount.negate().toJavaBigDecimal() * rate,
                    )
                }

            val allPositions = state.allPositions + currentPositions

            val totalInterestRate = calculateRate(allPositions, valuation.toJavaBigDecimal(), valuationDate)
            val previousAggregated = InterestRateCalculationCommand.Position(state.previousValuationDate, state.previousValuation.toJavaBigDecimal())
            val currentInterestRate = calculateRate(currentPositions + previousAggregated, valuation.toJavaBigDecimal(), valuationDate)

            val data = InterestRateDataTO(
                totalInterestRate = BigDecimal.parseString(totalInterestRate.toPlainString()),
                currentInterestRate = BigDecimal.parseString(currentInterestRate.toPlainString()),
            )

            val nextState = InterestRateState(
                allPositions = allPositions,
                instrumentUnits = totalInstrumentUnits,
                previousValuation = valuation,
                previousValuationDate = valuationDate,
            )

            AnalyticsBucketTO(dateTime, listOf(AnalyticsGroupBucketTO(value = data))) to nextState
        }
        return AnalyticsReportTO(granularity = interval.granularity, buckets = buckets)
    }

    private fun calculateRate(
        positions: List<InterestRateCalculationCommand.Position>,
        valuation: java.math.BigDecimal,
        valuationDate: LocalDate,
    ): java.math.BigDecimal {
        if (positions.none { it.date < valuationDate } || valuation <= java.math.BigDecimal.ZERO) {
            return java.math.BigDecimal.ZERO
        }
        return interestRateCalculator.calculateInterestRate(
            InterestRateCalculationCommand(positions = positions, valuation = valuation, valuationDate = valuationDate)
        )
    }

    private suspend fun convert(amounts: UnitAmounts, targetCurrency: Currency, date: LocalDate): BigDecimal {
        if (amounts.units.isEmpty()) return BigDecimal.ZERO
        val request = ConversionsRequest(amounts.units.map { ConversionRequest(it, targetCurrency, date) })
        val rates = conversionSdk.convert(request)
        return amounts.entries.fold(BigDecimal.ZERO) { acc, (unit, amount) ->
            val rate = rates.getRate(unit, targetCurrency, date)
            if (rate == null) {
                log.warn { "Conversion rate not found for $unit -> $targetCurrency on $date" }
                return@fold acc
            }
            acc + amount * rate
        }
    }

    private suspend fun getRate(source: FinancialUnit, target: Currency, date: LocalDate): java.math.BigDecimal {
        val response = conversionSdk.convert(ConversionsRequest(listOf(ConversionRequest(source, target, date))))
        return response.getRate(source, target, date)?.toJavaBigDecimal() ?: java.math.BigDecimal.ONE
    }

    private fun List<AnalyticsRecord>.toPositions(targetCurrency: Currency): List<InterestRateCalculationCommand.Position> =
        filter { it.unit.type == UnitType.CURRENCY }
            .map { record ->
                InterestRateCalculationCommand.Position(
                    date = record.dateTime.date,
                    amount = record.amount.negate().toJavaBigDecimal(),
                )
            }

    private fun BigDecimal.toJavaBigDecimal(): java.math.BigDecimal = java.math.BigDecimal(this.toPlainString())
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :service:analytics:analytics-service:test --tests "*.InterestRateServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/service/InterestRateService.kt
git add service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/service/InterestRateServiceTest.kt
git commit -m "feat: add InterestRateService with ungrouped interest rate report"
```

---

### Task 9: Wire New Services into Routing and DI

**Files:**
- Modify: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/web/AnalyticsApiRouting.kt`
- Modify: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/config/AnalyticsDependencies.kt`
- Modify: `service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/config/AnalyticsRouting.kt`

- [ ] **Step 1: Add new routes to AnalyticsApiRouting**

Add two new route handlers after the existing ones in `analyticsApiRouting`:

```kotlin
fun Routing.analyticsApiRouting(
    analyticsService: AnalyticsService,
    performanceService: PerformanceService,
    interestRateService: InterestRateService,
) {
    route("/funds-api/analytics/v1/reports") {
        post("/balance") {
            val userId = Uuid.fromString(call.userId().toString())
            val request = call.receive<AnalyticsReportRequestTO>()
            log.info { "Balance report request for user $userId: $request" }
            val interval = ReportInterval(request.granularity, request.from, request.to)
            val filter = AnalyticsRecordFilter(fundIds = request.fundIds, units = request.units)
            val report = analyticsService.getBalanceReport(userId, interval, filter, request.targetCurrency, request.groupBy)
            call.respond(HttpStatusCode.OK, report)
        }
        post("/net-change") {
            val userId = Uuid.fromString(call.userId().toString())
            val request = call.receive<AnalyticsReportRequestTO>()
            log.info { "Net change report request for user $userId: $request" }
            val interval = ReportInterval(request.granularity, request.from, request.to)
            val filter = AnalyticsRecordFilter(fundIds = request.fundIds, units = request.units)
            val report = analyticsService.getNetChangeReport(userId, interval, filter, request.targetCurrency, request.groupBy)
            call.respond(HttpStatusCode.OK, report)
        }
        post("/performance") {
            val userId = Uuid.fromString(call.userId().toString())
            val request = call.receive<AnalyticsReportRequestTO>()
            log.info { "Performance report request for user $userId: $request" }
            val interval = ReportInterval(request.granularity, request.from, request.to)
            val filter = AnalyticsRecordFilter(fundIds = request.fundIds, units = request.units)
            val report = performanceService.getPerformanceReport(userId, interval, filter, request.targetCurrency, request.groupBy)
            call.respond(HttpStatusCode.OK, report)
        }
        post("/interest-rate") {
            val userId = Uuid.fromString(call.userId().toString())
            val request = call.receive<AnalyticsReportRequestTO>()
            log.info { "Interest rate report request for user $userId: $request" }
            val interval = ReportInterval(request.granularity, request.from, request.to)
            val filter = AnalyticsRecordFilter(fundIds = request.fundIds, units = request.units)
            val report = interestRateService.getInterestRateReport(userId, interval, filter, request.targetCurrency, request.groupBy)
            call.respond(HttpStatusCode.OK, report)
        }
    }
}
```

- [ ] **Step 2: Register new services in AnalyticsDependencies**

In the `analyticsServiceDependencies` module, add:

```kotlin
private val Application.analyticsServiceDependencies
    get() = module {
        single<TransactionsCreatedHandler> { TransactionsCreatedHandler(get()) }
        single<AnalyticsService> { AnalyticsService(get(), get()) }
        single<InterestRateCalculator> { InterestRateCalculator() }
        single<PerformanceService> { PerformanceService(get(), get()) }
        single<InterestRateService> { InterestRateService(get(), get(), get()) }
    }
```

Add required imports for `InterestRateCalculator`, `PerformanceService`, `InterestRateService`.

- [ ] **Step 3: Update routing configuration**

```kotlin
// service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/config/AnalyticsRouting.kt
package ro.jf.funds.analytics.service.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import ro.jf.funds.analytics.service.service.AnalyticsService
import ro.jf.funds.analytics.service.service.InterestRateService
import ro.jf.funds.analytics.service.service.PerformanceService
import ro.jf.funds.analytics.service.web.analyticsApiRouting

fun Application.configureAnalyticsRouting() {
    routing {
        analyticsApiRouting(get<AnalyticsService>(), get<PerformanceService>(), get<InterestRateService>())
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `./gradlew :service:analytics:analytics-service:test`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/web/AnalyticsApiRouting.kt
git add service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/config/AnalyticsDependencies.kt
git add service/analytics/analytics-service/src/main/kotlin/ro/jf/funds/analytics/service/config/AnalyticsRouting.kt
git commit -m "feat: wire performance and interest-rate endpoints into analytics service"
```

---

### Task 10: Integration Tests for New Endpoints

**Files:**
- Modify: `service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/web/AnalyticsApiTest.kt`

- [ ] **Step 1: Write integration test for performance endpoint**

Add to `AnalyticsApiTest.kt`:

```kotlin
@Test
fun `given open position records - when requesting performance report - then returns investment metrics`(): Unit =
    testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig, conversionServiceConfig)

        val eur = Currency("EUR")
        val sxr8 = Instrument("SXR8")

        analyticsRecordRepository.saveAll(listOf(
            AnalyticsRecord(
                id = uuid4(), userId = userId, fundId = fundId, accountId = accountId,
                transactionId = uuid4(), transactionType = TransactionType.OPEN_POSITION,
                dateTime = LocalDateTime.parse("2024-01-15T10:00:00"),
                amount = BigDecimal.parseString("-500.00"), unit = eur, category = null,
            ),
            AnalyticsRecord(
                id = uuid4(), userId = userId, fundId = fundId, accountId = accountId,
                transactionId = uuid4(), transactionType = TransactionType.OPEN_POSITION,
                dateTime = LocalDateTime.parse("2024-01-15T10:00:00"),
                amount = BigDecimal.parseString("2.00"), unit = sxr8, category = null,
            ),
        ))

        val client = createJsonHttpClient()
        val response = client.post("/funds-api/analytics/v1/reports/performance") {
            contentType(ContentType.Application.Json)
            header(USER_ID_HEADER, userId)
            setBody(AnalyticsReportRequestTO(
                granularity = TimeGranularity.MONTHLY,
                from = LocalDateTime.parse("2024-01-01T00:00:00"),
                to = LocalDateTime.parse("2024-02-01T00:00:00"),
                targetCurrency = eur,
            ))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }
```

- [ ] **Step 2: Run integration tests**

Run: `./gradlew :service:analytics:analytics-service:test --tests "*.AnalyticsApiTest"`
Expected: All tests pass (the performance endpoint returns 200 OK).

- [ ] **Step 3: Commit**

```bash
git add service/analytics/analytics-service/src/test/kotlin/ro/jf/funds/analytics/service/web/AnalyticsApiTest.kt
git commit -m "test: add integration tests for performance and interest-rate endpoints"
```

---

### Task 11: Web Client - API Types and Fetch Functions

**Files:**
- Modify: `client/web-client/src/jsMain/resources/react/api/analyticsApi.ts`

- [ ] **Step 1: Update analyticsApi.ts with new types and functions**

```typescript
// client/web-client/src/jsMain/resources/react/api/analyticsApi.ts
import { handleApiError } from './apiUtils';

export type TimeGranularity = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';
export type GroupBy = 'FINANCIAL_UNIT' | 'ACCOUNT' | 'FUND' | 'CATEGORY';

export interface ReportRequest {
    granularity: TimeGranularity;
    from: string;
    to: string;
    fundIds?: string[];
    units?: { type: string; value: string }[];
    targetCurrency: string;
    groupBy?: GroupBy;
}

export interface GroupBucket<T = string> {
    groupKey: string | null;
    value: T;
}

export interface ReportBucket<T = string> {
    dateTime: string;
    groups: GroupBucket<T>[];
}

export interface ReportResponse<T = string> {
    granularity: TimeGranularity;
    buckets: ReportBucket<T>[];
}

export interface PerformanceData {
    totalInvestment: string;
    currentInvestment: string;
    totalProfit: string;
    currentProfit: string;
    totalInstrumentValue: string;
    currencyValue: string;
}

export interface InterestRateData {
    totalInterestRate: string;
    currentInterestRate: string;
}

declare const window: Window & {
    FUNDS_CONFIG?: { analyticsServiceUrl?: string };
};

function getBaseUrl(): string {
    const url = window.FUNDS_CONFIG?.analyticsServiceUrl;
    if (!url) {
        throw new Error('FUNDS_CONFIG.analyticsServiceUrl is not configured');
    }
    return url;
}

const BASE_PATH = '/funds-api/analytics/v1';

export async function getBalanceReport(
    userId: string,
    request: ReportRequest
): Promise<ReportResponse> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/reports/balance`, {
        method: 'POST',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });
    if (!response.ok) await handleApiError(response, 'Failed to load balance report');
    return response.json();
}

export async function getNetChangeReport(
    userId: string,
    request: ReportRequest
): Promise<ReportResponse> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/reports/net-change`, {
        method: 'POST',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });
    if (!response.ok) await handleApiError(response, 'Failed to load net change report');
    return response.json();
}

export async function getPerformanceReport(
    userId: string,
    request: ReportRequest
): Promise<ReportResponse<PerformanceData>> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/reports/performance`, {
        method: 'POST',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });
    if (!response.ok) await handleApiError(response, 'Failed to load performance report');
    return response.json();
}

export async function getInterestRateReport(
    userId: string,
    request: ReportRequest
): Promise<ReportResponse<InterestRateData>> {
    const response = await fetch(`${getBaseUrl()}${BASE_PATH}/reports/interest-rate`, {
        method: 'POST',
        headers: {
            'FUNDS_USER_ID': userId,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });
    if (!response.ok) await handleApiError(response, 'Failed to load interest rate report');
    return response.json();
}

export function extractMetric<T>(
    report: ReportResponse<T>,
    metric: keyof T,
): ReportResponse {
    return {
        granularity: report.granularity,
        buckets: report.buckets.map(bucket => ({
            dateTime: bucket.dateTime,
            groups: bucket.groups.map(group => ({
                groupKey: group.groupKey,
                value: String(group.value[metric]),
            })),
        })),
    };
}
```

- [ ] **Step 2: Commit**

```bash
git add client/web-client/src/jsMain/resources/react/api/analyticsApi.ts
git commit -m "feat: add performance and interest-rate API types and fetch functions"
```

---

### Task 12: Web Client - Analytics Page with Metric Selector

**Files:**
- Modify: `client/web-client/src/jsMain/resources/react/pages/AnalyticsPage.tsx`

- [ ] **Step 1: Update AnalyticsPage with new report types and metric selector**

Update the imports:

```typescript
import { getBalanceReport, getNetChangeReport, getPerformanceReport, getInterestRateReport, extractMetric, TimeGranularity, GroupBy, ReportResponse, PerformanceData, InterestRateData } from '../api/analyticsApi';
```

Update the `ReportType` and options:

```typescript
type ReportType = 'balance' | 'netChange' | 'performance' | 'interestRate';

const reportTypeOptions: { value: ReportType; label: string; seriesName: string; color: string }[] = [
    { value: 'balance', label: 'Balance', seriesName: 'Balance', color: '#2563eb' },
    { value: 'netChange', label: 'Net Change', seriesName: 'Net Change', color: '#16a34a' },
    { value: 'performance', label: 'Performance', seriesName: 'Performance', color: '#9333ea' },
    { value: 'interestRate', label: 'Interest Rate', seriesName: 'Interest Rate', color: '#ea580c' },
];
```

Update the `groupByOptions`:

```typescript
const groupByOptions: { value: string; label: string }[] = [
    { value: 'NONE', label: 'None' },
    { value: 'FINANCIAL_UNIT', label: 'Financial Unit' },
    { value: 'ACCOUNT', label: 'Account' },
    { value: 'FUND', label: 'Fund' },
    { value: 'CATEGORY', label: 'Category' },
];
```

Add metric options:

```typescript
const performanceMetrics: { value: keyof PerformanceData; label: string }[] = [
    { value: 'totalInvestment', label: 'Total Investment' },
    { value: 'currentInvestment', label: 'Current Investment' },
    { value: 'totalProfit', label: 'Total Profit' },
    { value: 'currentProfit', label: 'Current Profit' },
    { value: 'totalInstrumentValue', label: 'Total Instrument Value' },
    { value: 'currencyValue', label: 'Currency Value' },
];

const interestRateMetrics: { value: keyof InterestRateData; label: string }[] = [
    { value: 'totalInterestRate', label: 'Total Interest Rate' },
    { value: 'currentInterestRate', label: 'Current Interest Rate' },
];
```

Add state for selected metric inside the component:

```typescript
const [selectedMetric, setSelectedMetric] = useState<string>('totalProfit');
```

Update `loadData` to handle all report types:

```typescript
const loadData = async () => {
    if (!targetCurrency) return;
    setLoading(true);
    setError(null);
    try {
        const units = selectedUnits.map(key => {
            const [type, value] = key.split(':');
            return { type, value };
        });
        const request = {
            granularity,
            from: toLocalDateTime(fromDate),
            to: toLocalDateTime(toDate),
            fundIds: selectedFundIds.length > 0 ? selectedFundIds : undefined,
            units: units.length > 0 ? units : undefined,
            targetCurrency,
            groupBy: groupBy !== 'NONE' ? groupBy as GroupBy : undefined,
        };
        let data: ReportResponse;
        if (reportType === 'balance') {
            data = await getBalanceReport(userId, request);
        } else if (reportType === 'netChange') {
            data = await getNetChangeReport(userId, request);
        } else if (reportType === 'performance') {
            const perfData = await getPerformanceReport(userId, request);
            data = extractMetric(perfData, selectedMetric as keyof PerformanceData);
        } else {
            const rateData = await getInterestRateReport(userId, request);
            data = extractMetric(rateData, selectedMetric as keyof InterestRateData);
        }
        setReport(data);
    } catch (err) {
        setError('Failed to load analytics data: ' + (err instanceof Error ? err.message : 'Unknown error'));
    } finally {
        setLoading(false);
    }
};
```

Add a metric selector in the JSX after the Report selector (only rendered for performance/interestRate):

```tsx
{(reportType === 'performance' || reportType === 'interestRate') && (
    <div className="flex flex-col gap-1">
        <label className="text-sm text-muted-foreground">Metric</label>
        <Select value={selectedMetric} onValueChange={setSelectedMetric}>
            <SelectTrigger className="w-[200px] h-9">
                <SelectValue />
            </SelectTrigger>
            <SelectContent>
                {(reportType === 'performance' ? performanceMetrics : interestRateMetrics).map(m => (
                    <SelectItem key={m.value} value={m.value}>{m.label}</SelectItem>
                ))}
            </SelectContent>
        </Select>
    </div>
)}
```

Add an effect to reset metric when report type changes:

```typescript
useEffect(() => {
    if (reportType === 'performance') setSelectedMetric('totalProfit');
    else if (reportType === 'interestRate') setSelectedMetric('totalInterestRate');
}, [reportType]);
```

- [ ] **Step 2: Build to verify no TypeScript errors**

Run: `cd client/web-client && npx webpack --mode development`
Expected: Build succeeds without errors.

- [ ] **Step 3: Commit**

```bash
git add client/web-client/src/jsMain/resources/react/pages/AnalyticsPage.tsx
git commit -m "feat: add performance and interest-rate report types with metric selector to analytics page"
```

---

### Task 13: Full Build Verification

- [ ] **Step 1: Run full analytics service test suite**

Run: `./gradlew :service:analytics:analytics-service:test`
Expected: All tests pass.

- [ ] **Step 2: Run full project build**

Run: `./gradlew :service:analytics:analytics-api:build :service:analytics:analytics-service:build`
Expected: Build succeeds.

- [ ] **Step 3: Build web client**

Run: `./gradlew :client:web-client:jsBrowserDevelopmentWebpack`
Expected: Build succeeds.
