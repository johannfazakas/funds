package ro.jf.funds.reporting.service.domain

sealed class ReportingException : RuntimeException() {
    class ReportViewNotFound : ReportingException()
    class MissingGranularity : ReportingException()
    class MissingIntervalStart : ReportingException()
    class MissingIntervalEnd : ReportingException()
}
