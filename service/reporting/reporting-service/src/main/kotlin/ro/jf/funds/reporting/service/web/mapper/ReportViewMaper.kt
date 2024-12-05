package ro.jf.funds.reporting.service.web.mapper

import ro.jf.funds.reporting.api.model.ReportViewTO
import ro.jf.funds.reporting.service.domain.ReportView

fun ReportView.toTO(): ReportViewTO = ReportViewTO(
    id = id,
    name = name,
    fundId = fundId,
    type = type
)
