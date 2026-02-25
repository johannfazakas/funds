package ro.jf.funds.importer.api

import com.benasher44.uuid.Uuid
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.importer.api.model.ImportTaskTO

interface ImportApi {
    suspend fun import(userId: Uuid, fileType: ImportFileTypeTO, importConfiguration: ImportConfigurationTO, csvFile: ByteArray): ImportTaskTO
    suspend fun import(userId: Uuid, fileType: ImportFileTypeTO, importConfiguration: ImportConfigurationTO, csvFiles: List<ByteArray>): ImportTaskTO
    suspend fun getImportTask(userId: Uuid, taskId: Uuid): ImportTaskTO
}
