package ro.jf.funds.analytics.service.domain

import com.benasher44.uuid.Uuid
import ro.jf.funds.analytics.api.model.DashboardLookbackUnitTO
import ro.jf.funds.analytics.api.model.DashboardQueryTO
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.platform.api.model.Currency

data class DashboardLookback(
    val amount: Int,
    val unit: DashboardLookbackUnitTO,
)

data class Dashboard(
    val id: Uuid,
    val userId: Uuid,
    val name: String,
    val position: Int,
    val defaultGranularity: TimeGranularity,
    val defaultLookback: DashboardLookback,
    val defaultTargetCurrency: Currency,
    val charts: List<DashboardChart>,
)

data class DashboardChart(
    val id: Uuid,
    val name: String,
    val position: Int,
    val queries: List<DashboardQueryTO>,
)

data class CreateDashboard(
    val name: String,
    val defaultGranularity: TimeGranularity,
    val defaultLookback: DashboardLookback,
    val defaultTargetCurrency: Currency,
    val charts: List<CreateDashboardChart>,
)

data class UpdateDashboard(
    val name: String,
    val defaultGranularity: TimeGranularity,
    val defaultLookback: DashboardLookback,
    val defaultTargetCurrency: Currency,
)

data class CreateDashboardChart(
    val id: Uuid?,
    val name: String,
    val queries: List<DashboardQueryTO>,
)

data class UpdateDashboardChart(
    val name: String,
    val queries: List<DashboardQueryTO>,
)

class DashboardNotFoundException(val dashboardId: Uuid) : RuntimeException("Dashboard '$dashboardId' not found")

class DashboardChartNotFoundException(val chartId: Uuid) : RuntimeException("Dashboard chart '$chartId' not found")

class DashboardReorderException(message: String) : RuntimeException(message)
