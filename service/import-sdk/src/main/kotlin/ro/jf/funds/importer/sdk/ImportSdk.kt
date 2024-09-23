package ro.jf.funds.importer.sdk

import io.ktor.client.*
import ro.jf.funds.importer.api.ImportApi
import ro.jf.funds.importer.api.model.ImportRequest
import ro.jf.funds.importer.api.model.ImportResponse

const val LOCALHOST_BASE_URL = "http://localhost:5207"

class ImportSdk(
    private val httpClient: HttpClient,
    private val baseUrl: String = LOCALHOST_BASE_URL
) : ImportApi {
    override fun import(request: ImportRequest): ImportResponse {
        return ImportResponse("Imported in sdk")
    }
}
