package ro.jf.funds.importer.service.domain.port

import ro.jf.funds.importer.service.domain.model.ImportConfiguration
import java.util.*

interface ImportService {
    suspend fun import(userId: UUID, configuration: ImportConfiguration, files: List<String>)
}
