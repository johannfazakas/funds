package ro.jf.funds.importer.api

import ro.jf.funds.importer.api.model.ImportRequest
import ro.jf.funds.importer.api.model.ImportResponse

interface ImportApi {
    fun import(request: ImportRequest): ImportResponse
}