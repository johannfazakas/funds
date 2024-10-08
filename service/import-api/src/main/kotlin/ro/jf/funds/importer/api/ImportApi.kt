package ro.jf.funds.importer.api

import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportResponse
import java.io.File
import java.util.*

interface ImportApi {
    suspend fun import(userId: UUID, csvFileSource: File, importConfiguration: ImportConfigurationTO): ImportResponse
}
