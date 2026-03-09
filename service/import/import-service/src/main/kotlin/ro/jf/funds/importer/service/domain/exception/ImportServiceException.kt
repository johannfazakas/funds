package ro.jf.funds.importer.service.domain.exception

import com.benasher44.uuid.Uuid

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

class ImportConfigurationValidationException(message: String) : ImportServiceException(message)

class ImportFileNotFoundException(val importFileId: Uuid) : ImportServiceException()

class ImportFileStatusConflictException(val importFileId: Uuid) : ImportServiceException()

class ImportConfigurationNotFoundException(val importConfigurationId: Uuid) : ImportServiceException()

class ImportConfigurationInUseException(val importConfigurationId: Uuid, message: String) : ImportServiceException(message)
