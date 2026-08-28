package ro.jf.funds.analytics.service.service

import com.benasher44.uuid.Uuid
import ro.jf.funds.analytics.service.domain.CreateDashboard
import ro.jf.funds.analytics.service.domain.CreateDashboardChart
import ro.jf.funds.analytics.service.domain.Dashboard
import ro.jf.funds.analytics.service.domain.DashboardChart
import ro.jf.funds.analytics.service.domain.DashboardChartNotFoundException
import ro.jf.funds.analytics.service.domain.DashboardNotFoundException
import ro.jf.funds.analytics.service.domain.UpdateDashboard
import ro.jf.funds.analytics.service.domain.UpdateDashboardChart
import ro.jf.funds.analytics.service.persistence.DashboardRepository

class DashboardService(
    private val repository: DashboardRepository,
) {
    suspend fun listDashboards(userId: Uuid): List<Dashboard> = repository.list(userId)

    suspend fun getDashboard(userId: Uuid, dashboardId: Uuid): Dashboard =
        repository.getById(userId, dashboardId) ?: throw DashboardNotFoundException(dashboardId)

    suspend fun createDashboard(userId: Uuid, create: CreateDashboard): Dashboard = repository.create(userId, create)

    suspend fun reorderDashboards(userId: Uuid, dashboardIds: List<Uuid>): List<Dashboard> =
        repository.reorder(userId, dashboardIds)

    suspend fun updateDashboard(userId: Uuid, dashboardId: Uuid, update: UpdateDashboard): Dashboard =
        repository.update(userId, dashboardId, update) ?: throw DashboardNotFoundException(dashboardId)

    suspend fun deleteDashboard(userId: Uuid, dashboardId: Uuid) {
        if (!repository.delete(userId, dashboardId)) throw DashboardNotFoundException(dashboardId)
    }

    suspend fun appendChart(userId: Uuid, dashboardId: Uuid, chart: CreateDashboardChart): DashboardChart =
        repository.appendChart(userId, dashboardId, chart) ?: throw DashboardNotFoundException(dashboardId)

    suspend fun updateChart(
        userId: Uuid,
        dashboardId: Uuid,
        chartId: Uuid,
        update: UpdateDashboardChart,
    ): DashboardChart =
        repository.updateChart(userId, dashboardId, chartId, update) ?: throw DashboardChartNotFoundException(chartId)

    suspend fun deleteChart(userId: Uuid, dashboardId: Uuid, chartId: Uuid) {
        if (!repository.deleteChart(userId, dashboardId, chartId)) throw DashboardChartNotFoundException(chartId)
    }

    suspend fun reorderCharts(userId: Uuid, dashboardId: Uuid, chartIds: List<Uuid>): List<DashboardChart> =
        repository.reorderCharts(userId, dashboardId, chartIds) ?: throw DashboardNotFoundException(dashboardId)
}
