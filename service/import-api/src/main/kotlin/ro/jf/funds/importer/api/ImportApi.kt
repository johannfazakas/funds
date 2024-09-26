package ro.jf.funds.importer.api

import ro.jf.funds.importer.api.model.ImportResponse
import java.io.File
import java.util.*
import ro.jf.funds.importer.api.model.CsvFormattingTO

interface ImportApi {
    suspend fun import(userId: UUID, csvFileSource: File, csvFormatting: CsvFormattingTO): ImportResponse
}
