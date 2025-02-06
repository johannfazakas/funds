package ro.jf.funds.reporting.service.domain

import kotlinx.datetime.LocalDate
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.FinancialUnit
import java.util.UUID


sealed class ReportingException : RuntimeException() {
    class ReportViewNotFound(val userId: UUID, val reportViewId: UUID) : ReportingException()
    class ReportViewAlreadyExists(val userId: UUID, val reportViewName: String) : ReportingException()
    class ReportRecordConversionRateNotFound(val recordId: UUID) : ReportingException()

    class MissingGranularity : ReportingException()
    class MissingIntervalStart : ReportingException()
    class MissingIntervalEnd : ReportingException()
}
