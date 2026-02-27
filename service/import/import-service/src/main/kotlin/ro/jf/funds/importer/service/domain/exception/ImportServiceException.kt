package ro.jf.funds.importer.service.domain.exception

import java.util.*

sealed class ImportServiceException : RuntimeException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

class ImportFormatException : ImportServiceException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

class ImportDataException(message: String) : ImportServiceException(message)

class MissingImportConfigurationException : ImportServiceException()

class ImportConfigurationValidationException(message: String) : ImportServiceException(message)

class ImportFileNotFoundException(val importFileId: UUID) : ImportServiceException()

class ImportFileStatusConflictException(val importFileId: UUID) : ImportServiceException()

class ImportConfigurationNotFoundException(val importConfigurationId: UUID) : ImportServiceException()
