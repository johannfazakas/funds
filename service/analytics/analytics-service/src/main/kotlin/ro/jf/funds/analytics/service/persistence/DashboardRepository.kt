package ro.jf.funds.analytics.service.persistence

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.json.json
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import ro.jf.funds.analytics.api.model.DashboardLookbackUnitTO
import ro.jf.funds.analytics.api.model.DashboardQueryTO
import ro.jf.funds.analytics.api.model.TimeGranularity
import ro.jf.funds.analytics.service.domain.CreateDashboard
import ro.jf.funds.analytics.service.domain.CreateDashboardChart
import ro.jf.funds.analytics.service.domain.Dashboard
import ro.jf.funds.analytics.service.domain.DashboardChart
import ro.jf.funds.analytics.service.domain.DashboardLookback
import ro.jf.funds.analytics.service.domain.DashboardReorderException
import ro.jf.funds.analytics.service.domain.UpdateDashboard
import ro.jf.funds.analytics.service.domain.UpdateDashboardChart
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.jvm.persistence.blockingTransaction
import java.util.UUID

private val queriesJson = Json { ignoreUnknownKeys = true }

class DashboardRepository(
    private val database: Database,
) {
    object DashboardTable : UUIDTable("dashboard") {
        val userId = uuid("user_id")
        val name = varchar("name", 255)
        val position = integer("position")
        val defaultGranularity = varchar("default_granularity", 20)
        val defaultLookbackAmount = integer("default_lookback_amount")
        val defaultLookbackUnit = varchar("default_lookback_unit", 20)
        val defaultTargetCurrency = varchar("default_target_currency", 20)
    }

    object DashboardChartTable : UUIDTable("dashboard_chart") {
        val dashboardId = uuid("dashboard_id")
        val name = varchar("name", 255)
        val position = integer("position")
        val queries = json<List<DashboardQueryTO>>("queries", queriesJson)
    }

    suspend fun list(userId: Uuid): List<Dashboard> = blockingTransaction {
        listDashboards(userId)
    }

    suspend fun reorder(userId: Uuid, dashboardIds: List<Uuid>): List<Dashboard> = blockingTransaction {
        val storedIds = DashboardTable.selectAll()
            .where { DashboardTable.userId eq userId.toJavaUuid() }
            .map { Uuid.fromString(it[DashboardTable.id].value.toString()) }
            .toSet()
        val missingIds = storedIds - dashboardIds.toSet()
        val unknownIds = dashboardIds.toSet() - storedIds
        if (missingIds.isNotEmpty() || unknownIds.isNotEmpty()) {
            throw DashboardReorderException(
                "Reorder must reference each dashboard exactly once, missing: $missingIds, unknown: $unknownIds"
            )
        }
        dashboardIds.forEachIndexed { index, dashboardId ->
            DashboardTable.update({ DashboardTable.id eq dashboardId.toJavaUuid() }) {
                it[position] = index
            }
        }
        listDashboards(userId)
    }

    private fun listDashboards(userId: Uuid): List<Dashboard> {
        val dashboardRows = DashboardTable.selectAll()
            .where { DashboardTable.userId eq userId.toJavaUuid() }
            .orderBy(DashboardTable.position)
            .toList()
        val chartRowsByDashboard = DashboardChartTable.selectAll()
            .where { DashboardChartTable.dashboardId inList dashboardRows.map { it[DashboardTable.id].value } }
            .orderBy(DashboardChartTable.position)
            .groupBy { it[DashboardChartTable.dashboardId] }
        return dashboardRows.map { it.toDashboard(chartRowsByDashboard[it[DashboardTable.id].value].orEmpty()) }
    }

    suspend fun getById(userId: Uuid, dashboardId: Uuid): Dashboard? = blockingTransaction {
        findDashboardRow(userId, dashboardId)?.toDashboard(chartRows(dashboardId))
    }

    suspend fun create(userId: Uuid, create: CreateDashboard): Dashboard = blockingTransaction {
        val dashboardId = uuid4()
        val position = DashboardTable.selectAll()
            .where { DashboardTable.userId eq userId.toJavaUuid() }
            .maxOfOrNull { it[DashboardTable.position] + 1 } ?: 0
        DashboardTable.insert {
            it[DashboardTable.id] = dashboardId.toJavaUuid()
            it[DashboardTable.userId] = userId.toJavaUuid()
            it[name] = create.name
            it[DashboardTable.position] = position
            it[defaultGranularity] = create.defaultGranularity.name
            it[defaultLookbackAmount] = create.defaultLookback.amount
            it[defaultLookbackUnit] = create.defaultLookback.unit.name
            it[defaultTargetCurrency] = create.defaultTargetCurrency.value
        }
        insertCharts(dashboardId, create.charts)
        findDashboardRow(userId, dashboardId)!!.toDashboard(chartRows(dashboardId))
    }

    suspend fun update(userId: Uuid, dashboardId: Uuid, update: UpdateDashboard): Dashboard? = blockingTransaction {
        findDashboardRow(userId, dashboardId) ?: return@blockingTransaction null
        DashboardTable.update({ DashboardTable.id eq dashboardId.toJavaUuid() }) {
            it[name] = update.name
            it[defaultGranularity] = update.defaultGranularity.name
            it[defaultLookbackAmount] = update.defaultLookback.amount
            it[defaultLookbackUnit] = update.defaultLookback.unit.name
            it[defaultTargetCurrency] = update.defaultTargetCurrency.value
        }
        findDashboardRow(userId, dashboardId)!!.toDashboard(chartRows(dashboardId))
    }

    suspend fun delete(userId: Uuid, dashboardId: Uuid): Boolean = blockingTransaction {
        val javaDashboardId = dashboardId.toJavaUuid()
        findDashboardRow(userId, dashboardId) ?: return@blockingTransaction false
        DashboardChartTable.deleteWhere { DashboardChartTable.dashboardId eq javaDashboardId }
        DashboardTable.deleteWhere { DashboardTable.id eq javaDashboardId }
        true
    }

    suspend fun appendChart(userId: Uuid, dashboardId: Uuid, chart: CreateDashboardChart): DashboardChart? =
        blockingTransaction {
            findDashboardRow(userId, dashboardId) ?: return@blockingTransaction null
            val position = chartRows(dashboardId).maxOfOrNull { it[DashboardChartTable.position] + 1 } ?: 0
            val chartId = chart.id ?: uuid4()
            DashboardChartTable.insert {
                it[DashboardChartTable.id] = chartId.toJavaUuid()
                it[DashboardChartTable.dashboardId] = dashboardId.toJavaUuid()
                it[name] = chart.name
                it[DashboardChartTable.position] = position
                it[queries] = chart.queries
            }
            DashboardChart(id = chartId, name = chart.name, position = position, queries = chart.queries)
        }

    suspend fun updateChart(
        userId: Uuid,
        dashboardId: Uuid,
        chartId: Uuid,
        update: UpdateDashboardChart,
    ): DashboardChart? = blockingTransaction {
        findDashboardRow(userId, dashboardId) ?: return@blockingTransaction null
        findChartRow(dashboardId, chartId) ?: return@blockingTransaction null
        DashboardChartTable.update({ DashboardChartTable.id eq chartId.toJavaUuid() }) {
            it[name] = update.name
            it[queries] = update.queries
        }
        findChartRow(dashboardId, chartId)!!.toChart()
    }

    suspend fun deleteChart(userId: Uuid, dashboardId: Uuid, chartId: Uuid): Boolean = blockingTransaction {
        findDashboardRow(userId, dashboardId) ?: return@blockingTransaction false
        findChartRow(dashboardId, chartId) ?: return@blockingTransaction false
        DashboardChartTable.deleteWhere { DashboardChartTable.id eq chartId.toJavaUuid() }
        true
    }

    suspend fun reorderCharts(userId: Uuid, dashboardId: Uuid, chartIds: List<Uuid>): List<DashboardChart>? =
        blockingTransaction {
            findDashboardRow(userId, dashboardId) ?: return@blockingTransaction null
            val storedIds = chartRows(dashboardId)
                .map { Uuid.fromString(it[DashboardChartTable.id].value.toString()) }
                .toSet()
            val missingIds = storedIds - chartIds.toSet()
            val unknownIds = chartIds.toSet() - storedIds
            if (missingIds.isNotEmpty() || unknownIds.isNotEmpty()) {
                throw DashboardReorderException(
                    "Reorder must reference each chart exactly once, missing: $missingIds, unknown: $unknownIds"
                )
            }
            chartIds.forEachIndexed { index, chartId ->
                DashboardChartTable.update({ DashboardChartTable.id eq chartId.toJavaUuid() }) {
                    it[position] = index
                }
            }
            chartRows(dashboardId).map { it.toChart() }
        }

    private fun insertCharts(dashboardId: Uuid, charts: List<CreateDashboardChart>) {
        DashboardChartTable.batchInsert(charts.withIndex().toList()) { (index, chart) ->
            this[DashboardChartTable.id] = (chart.id ?: uuid4()).toJavaUuid()
            this[DashboardChartTable.dashboardId] = dashboardId.toJavaUuid()
            this[DashboardChartTable.name] = chart.name
            this[DashboardChartTable.position] = index
            this[DashboardChartTable.queries] = chart.queries
        }
    }

    private fun findDashboardRow(userId: Uuid, dashboardId: Uuid): ResultRow? = DashboardTable.selectAll()
        .where { DashboardTable.id eq dashboardId.toJavaUuid() }
        .andWhere { DashboardTable.userId eq userId.toJavaUuid() }
        .singleOrNull()

    private fun findChartRow(dashboardId: Uuid, chartId: Uuid): ResultRow? = DashboardChartTable.selectAll()
        .where { DashboardChartTable.id eq chartId.toJavaUuid() }
        .andWhere { DashboardChartTable.dashboardId eq dashboardId.toJavaUuid() }
        .singleOrNull()

    private fun chartRows(dashboardId: Uuid): List<ResultRow> = DashboardChartTable.selectAll()
        .where { DashboardChartTable.dashboardId eq dashboardId.toJavaUuid() }
        .orderBy(DashboardChartTable.position)
        .toList()

    private fun ResultRow.toDashboard(chartRows: List<ResultRow>): Dashboard = Dashboard(
        id = Uuid.fromString(this[DashboardTable.id].toString()),
        userId = Uuid.fromString(this[DashboardTable.userId].toString()),
        name = this[DashboardTable.name],
        position = this[DashboardTable.position],
        defaultGranularity = TimeGranularity.valueOf(this[DashboardTable.defaultGranularity]),
        defaultLookback = DashboardLookback(
            amount = this[DashboardTable.defaultLookbackAmount],
            unit = DashboardLookbackUnitTO.valueOf(this[DashboardTable.defaultLookbackUnit]),
        ),
        defaultTargetCurrency = Currency(this[DashboardTable.defaultTargetCurrency]),
        charts = chartRows.map { it.toChart() },
    )

    private fun ResultRow.toChart(): DashboardChart = DashboardChart(
        id = Uuid.fromString(this[DashboardChartTable.id].toString()),
        name = this[DashboardChartTable.name],
        position = this[DashboardChartTable.position],
        queries = this[DashboardChartTable.queries],
    )

    private fun Uuid.toJavaUuid(): UUID = UUID.fromString(this.toString())
}
