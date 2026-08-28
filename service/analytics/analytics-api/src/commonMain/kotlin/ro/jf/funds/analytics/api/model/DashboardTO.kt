package ro.jf.funds.analytics.api.model

import com.benasher44.uuid.Uuid
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
data class DashboardTO(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    val name: String,
    val position: Int,
    val defaultGranularity: TimeGranularity,
    val defaultLookback: DashboardLookbackTO,
    val defaultTargetCurrency: Currency,
    val charts: List<DashboardChartTO>,
)

@Serializable
data class CreateDashboardTO(
    val name: String,
    val defaultGranularity: TimeGranularity,
    val defaultLookback: DashboardLookbackTO,
    val defaultTargetCurrency: Currency,
    val charts: List<CreateDashboardChartTO> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "Dashboard name must not be blank" }
    }
}

@Serializable
data class UpdateDashboardTO(
    val name: String,
    val defaultGranularity: TimeGranularity,
    val defaultLookback: DashboardLookbackTO,
    val defaultTargetCurrency: Currency,
) {
    init {
        require(name.isNotBlank()) { "Dashboard name must not be blank" }
    }
}

@Serializable
data class UpdateDashboardPositionsTO(
    val dashboardIds: List<@Serializable(with = UuidSerializer::class) Uuid>,
) {
    init {
        require(dashboardIds.isNotEmpty()) { "Dashboard ids must not be empty" }
        val duplicateIds = dashboardIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "Dashboard ids must be unique, duplicated: $duplicateIds" }
    }
}

@Serializable
data class DashboardChartTO(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    val name: String,
    val position: Int,
    val queries: List<DashboardQueryTO>,
)

@Serializable
data class CreateDashboardChartTO(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid? = null,
    val name: String,
    val queries: List<DashboardQueryTO>,
) {
    init {
        validateChart(name, queries)
    }
}

@Serializable
data class UpdateDashboardChartTO(
    val name: String,
    val queries: List<DashboardQueryTO>,
) {
    init {
        validateChart(name, queries)
    }
}

@Serializable
data class UpdateDashboardChartPositionsTO(
    val chartIds: List<@Serializable(with = UuidSerializer::class) Uuid>,
) {
    init {
        require(chartIds.isNotEmpty()) { "Chart ids must not be empty" }
        val duplicateIds = chartIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "Chart ids must be unique, duplicated: $duplicateIds" }
    }
}

@Serializable
data class DashboardQueryTO(
    val id: String,
    val label: String,
    val metric: MetricTO,
    val grouping: GroupingCriteria? = null,
    val filter: ReportFilterTO = ReportFilterTO(),
) {
    init {
        require(id.isNotBlank()) { "Query id must not be blank" }
        require(label.isNotBlank()) { "Query label must not be blank" }
    }
}

@Serializable
data class DashboardLookbackTO(
    val amount: Int,
    val unit: DashboardLookbackUnitTO,
) {
    init {
        require(amount > 0) { "Lookback amount must be strictly positive" }
    }
}

enum class DashboardLookbackUnitTO {
    DAY,
    WEEK,
    MONTH,
    YEAR,
}

private fun validateChart(name: String, queries: List<DashboardQueryTO>) {
    require(name.isNotBlank()) { "Chart name must not be blank" }
    require(queries.isNotEmpty()) { "Chart must define at least one query" }
    val duplicateIds = queries.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    require(duplicateIds.isEmpty()) { "Chart query ids must be unique, duplicated: $duplicateIds" }
}
