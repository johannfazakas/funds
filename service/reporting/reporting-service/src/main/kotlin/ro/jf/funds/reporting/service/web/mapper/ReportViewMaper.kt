package ro.jf.funds.reporting.service.web.mapper

import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.service.domain.ReportView

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
