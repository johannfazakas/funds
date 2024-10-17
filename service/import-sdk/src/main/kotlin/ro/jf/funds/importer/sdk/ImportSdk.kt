package ro.jf.funds.importer.sdk

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging.logger
import ro.jf.bk.commons.model.ResponseTO
import ro.jf.bk.commons.web.USER_ID_HEADER
import ro.jf.funds.commons.sdk.client.createHttpClient
import ro.jf.funds.commons.sdk.client.toResponseTO
import ro.jf.funds.importer.api.ImportApi
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportResponse
import java.io.File
import java.util.*

private const val LOCALHOST_BASE_URL = "http://localhost:5207"

private val log = logger { }

class ImportSdk(
    private val baseUrl: String = LOCALHOST_BASE_URL,
    private val httpClient: HttpClient = createHttpClient(),
) : ImportApi {
    override suspend fun import(
        userId: UUID,
        importConfiguration: ImportConfigurationTO,
        csvFile: File
    ): ResponseTO<ImportResponse> {
        return import(userId, importConfiguration, listOf(csvFile))
    }

    override suspend fun import(
        userId: UUID,
        importConfiguration: ImportConfigurationTO,
        csvFiles: List<File>
    ): ResponseTO<ImportResponse> {
        log.info { "Importing CSV files ${csvFiles.map { it.name }} for user $userId." }
        val response: HttpResponse = httpClient.post("$baseUrl/bk-api/import/v1/imports") {
            header(USER_ID_HEADER, userId.toString())
            setBody(MultiPartFormDataContent(
                formData {
                    csvFiles.forEachIndexed { index, csvFile ->
                        append("file-$index", csvFile.readBytes(), Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Text.CSV)
                            append(HttpHeaders.ContentDisposition, "filename=\"${csvFile.name}\"")
                        })
                    }
                    append("configuration", Json.encodeToString(importConfiguration), Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                    })
                }
            ))
        }
        return response.toResponseTO()
    }
}
