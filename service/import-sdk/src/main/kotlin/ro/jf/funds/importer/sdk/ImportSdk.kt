package ro.jf.funds.importer.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging.logger
import ro.jf.bk.commons.web.USER_ID_HEADER
import ro.jf.funds.commons.sdk.createHttpClient
import ro.jf.funds.importer.api.ImportApi
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportResponse
import java.io.File
import java.util.*

private const val LOCALHOST_BASE_URL = "http://localhost:5207"

private val log = logger { }

class ImportSdk(
    // TODO(Johann) this could have a default, would be easier to configure in the notebook
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : ImportApi {
    override suspend fun import(
        userId: UUID,
        csvFileSource: File,
        importConfiguration: ImportConfigurationTO
    ): ImportResponse {
        log.info { "Importing CSV file ${csvFileSource.name} for user $userId." }
        val response = httpClient.post("$baseUrl/bk-api/import/v1/imports") {
            header(USER_ID_HEADER, userId.toString())
            setBody(MultiPartFormDataContent(
                formData {
                    append("file", csvFileSource.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Text.CSV)
                        append(HttpHeaders.ContentDisposition, "filename=\"${csvFileSource.name}\"")
                    })
                    append("configuration", Json.encodeToString(importConfiguration), Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                    })
                }
            ))
        }
        // TODO(Johann) should respond with some ErrorResponse when status is not OK
        return response.body<ImportResponse>()
    }
}
