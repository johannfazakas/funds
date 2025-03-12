package ro.jf.funds.reporting.service.domain

import java.util.*


sealed class ReportingException : RuntimeException() {
    class ReportViewNotFound(val userId: UUID, val reportViewId: UUID) : ReportingException()
    class ReportViewAlreadyExists(val userId: UUID, val reportViewName: String) : ReportingException()
    class ReportRecordConversionRateNotFound(val recordId: UUID) : ReportingException()
    class MissingGroupsRequiredForFeature(val userId: UUID, val featureName: String): ReportingException()
    class MissingGranularity : ReportingException()
    class MissingIntervalStart : ReportingException()
    class MissingIntervalEnd : ReportingException()
}
