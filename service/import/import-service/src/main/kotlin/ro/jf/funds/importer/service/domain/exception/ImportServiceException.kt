package ro.jf.funds.importer.service.domain.exception

sealed class ImportServiceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ImportFormatException(message: String, cause: Throwable? = null) : ImportServiceException(message, cause)

class ImportDataException(message: String, cause: Throwable? = null) : ImportServiceException(message, cause)

class MissingImportConfigurationException(message: String) : ImportServiceException(message)
