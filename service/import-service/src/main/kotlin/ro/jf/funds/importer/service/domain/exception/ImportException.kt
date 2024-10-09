package ro.jf.funds.importer.service.domain.exception

sealed class ImportException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ImportFormatException(message: String, cause: Throwable? = null) : ImportException(message, cause)

class ImportDataException(message: String, cause: Throwable? = null) : ImportException(message, cause)
