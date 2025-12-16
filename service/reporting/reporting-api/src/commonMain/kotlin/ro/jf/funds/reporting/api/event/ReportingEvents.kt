package ro.jf.funds.reporting.api.event

import ro.jf.funds.platform.api.event.EventType

object ReportingEvents {
    private const val REPORTING_DOMAIN = "reporting"
    private const val REPORT_VIEW_RESOURCE = "report_view"
    private const val CREATE_REQUEST = "create-request"

    val FundTransactionsBatchRequest = EventType(REPORTING_DOMAIN, REPORT_VIEW_RESOURCE, CREATE_REQUEST)
}
