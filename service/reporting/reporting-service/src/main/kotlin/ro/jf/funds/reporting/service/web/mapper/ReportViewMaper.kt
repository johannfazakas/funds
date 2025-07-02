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
        reports = ReportsConfigurationTO(
            net = dataConfiguration.reports.net.let {
                NetReportConfigurationTO(
                    enabled = it.enabled,
                    filter = it.filter?.toTO()
                )
            },
            valueReport = ValueReportConfigurationTO(
                enabled = dataConfiguration.reports.valueReport.enabled,
                filter = dataConfiguration.reports.valueReport.filter?.toTO()
            ),
            groupedNet = GenericReportConfigurationTO(
                enabled = dataConfiguration.reports.groupedNet.enabled
            ),
            groupedBudget = dataConfiguration.reports.groupedBudget.let {
                GroupedBudgetReportConfigurationTO(
                    enabled = it.enabled,
                    distributions = it.distributions.map { distribution ->
                        GroupedBudgetReportConfigurationTO.BudgetDistributionTO(
                            default = distribution.default,
                            from = distribution.from?.let { YearMonthTO(year = it.year, month = it.month) },
                            groups = distribution.groups.map { group ->
                                GroupedBudgetReportConfigurationTO.GroupBudgetPercentageTO(
                                    group = group.group,
                                    percentage = group.percentage
                                )
                            }
                        )
                    }
                )
            }
        ),
    ),
)

fun RecordFilter.toTO(): RecordFilterTO = RecordFilterTO(labels = labels)

fun CreateReportViewTO.toDomain(userId: UUID) = CreateReportViewCommand(
    userId = userId,
    name = name,
    fundId = fundId,
    dataConfiguration = dataConfiguration.toDomain(),
)

fun ReportDataConfigurationTO.toDomain() = ReportDataConfiguration(
    currency = currency,
    groups = groups?.map { ReportGroup(it.name, RecordFilter(it.filter.labels)) },
    reports = ReportsConfiguration(
        net = NetReportConfiguration(reports.net.enabled, reports.net.filter?.let { RecordFilter(it.labels) }),
        valueReport = ValueReportConfiguration(
            reports.valueReport.enabled,
            reports.valueReport.filter?.let { RecordFilter(it.labels) }),
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
