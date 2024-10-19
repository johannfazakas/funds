package ro.jf.funds.importer.service.domain.exception

sealed class ImportException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ImportFormatException(message: String, cause: Throwable? = null) : ImportException(message, cause)

// TODO(Johann) could actually be more detailed?
class ImportDataException(message: String, cause: Throwable? = null) : ImportException(message, cause)
