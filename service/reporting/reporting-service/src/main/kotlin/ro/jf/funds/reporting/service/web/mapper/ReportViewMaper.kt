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
        features = ReportDataFeaturesConfigurationTO(
            net = NetReportFeatureTO(enabled = true, applyFilter = true),
            valueReport = GenericReportFeatureTO(enabled = true),
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
    features = ReportDataFeaturesConfiguration(
        net = NetReportFeature(features.net.enabled, features.net.applyFilter),
        valueReport = GenericReportFeature(features.valueReport.enabled),
        groupedNet = GenericReportFeature(features.groupedNet.enabled),
    ),
)
