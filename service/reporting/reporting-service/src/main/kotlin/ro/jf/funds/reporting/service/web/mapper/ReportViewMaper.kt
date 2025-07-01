package ro.jf.funds.reporting.service.web.mapper

import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.service.domain.*
import java.util.*

fun ReportView.toTO(): ReportViewTO = ReportViewTO(
    id = id,
    name = name,
    fundId = fundId,
    dataConfiguration = ReportDataConfigurationTO(
        currency = dataConfiguration.currency,
        filter = RecordFilterTO(dataConfiguration.filter.labels ?: emptyList()),
        reports = ReportsConfigurationTO(
            net = NetReportConfigurationTO(enabled = true, applyFilter = true),
            valueReport = GenericReportConfigurationTO(enabled = true),
        ),
    ),
)

fun CreateReportViewTO.toDomain(userId: UUID) = CreateReportViewCommand(
    userId = userId,
    name = name,
    fundId = fundId,
    dataConfiguration = dataConfiguration.toDomain(),
)

fun ReportDataConfigurationTO.toDomain() = ReportDataConfiguration(
    currency = currency,
    filter = RecordFilter(filter.labels),
    groups = groups?.map { ReportGroup(it.name, RecordFilter(it.filter.labels)) },
    reports = ReportsConfiguration(
        net = NetReportConfiguration(reports.net.enabled, reports.net.applyFilter),
        valueReport = GenericReportConfiguration(reports.valueReport.enabled),
        groupedNet = GenericReportConfiguration(reports.groupedNet.enabled),
        groupedBudget = reports.groupedBudget.toDomain(),
    ),
    forecast = ForecastConfiguration(
        inputBuckets = forecast.inputBuckets,
    )
)

private fun GroupedBudgetReportConfigurationTO.toDomain() =
    GroupedBudgetReportConfiguration(
        enabled = enabled,
        distributions = distributions.map { distribution: GroupedBudgetReportConfigurationTO.BudgetDistributionTO ->
            GroupedBudgetReportConfiguration.BudgetDistribution(
                default = distribution.default,
                from = distribution.from?.toDomain(),
                groups = distribution.groups.map { group ->
                    GroupedBudgetReportConfiguration.GroupBudgetPercentage(group.group, group.percentage)
                }
            )
        }
    )

private fun YearMonthTO.toDomain() = this.let { YearMonth(it.year, it.month) }
