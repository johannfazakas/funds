package ro.jf.funds.reporting.api.exception

// TODO(Johann) could have another base common exception which could also be linked to a problem schema
sealed class ReportingApiException(override val message: String) : RuntimeException(message) {
    class Generic(val reason: Any? = null) : ReportingApiException("Internal error.")
}
