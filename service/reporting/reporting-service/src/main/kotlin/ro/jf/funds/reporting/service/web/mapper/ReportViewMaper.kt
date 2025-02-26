package ro.jf.funds.reporting.service.web.mapper

import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.service.domain.ReportView

fun ReportView.toTO(): ReportViewTO = ReportViewTO(
    id = id,
    name = name,
    fundId = fundId,
    dataConfiguration = ReportDataConfigurationTO(
        currency = currency,
        filter = RecordFilterTO.byLabels(*labels.map { it.value }.toTypedArray()),
        features = ReportDataFeaturesConfigurationTO(
            net = NetReportFeatureTO(enabled = true, applyFilter = true),
            valueReport = GenericReportFeatureTO(enabled = true),
        ),
    ),
)
