package ro.jf.funds.importer.api

import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportTaskTO
import java.io.File
import java.util.*

interface ImportApi {
    suspend fun import(userId: UUID, importConfiguration: ImportConfigurationTO, csvFile: File): ImportTaskTO
    suspend fun import(userId: UUID, importConfiguration: ImportConfigurationTO, csvFiles: List<File>): ImportTaskTO
}
