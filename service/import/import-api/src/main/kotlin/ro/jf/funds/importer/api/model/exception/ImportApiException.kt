package ro.jf.funds.importer.api.model.exception

sealed class ImportApiException(override val message: String) : RuntimeException(message) {
    class Generic(val reason: Any? = null) : ImportApiException("Internal error.")
    class DataException(message: String) : ImportApiException(message)
    class FormatException(message: String) : ImportApiException(message)
}
