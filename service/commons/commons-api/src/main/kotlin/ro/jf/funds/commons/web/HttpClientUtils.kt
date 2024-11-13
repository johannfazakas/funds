package ro.jf.funds.commons.web

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ro.jf.funds.commons.error.ApiException
import ro.jf.funds.commons.error.ErrorTO

fun createHttpClient() = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 60000
    }
}

private const val GENERIC_ERROR = "Generic error"

suspend fun HttpResponse.toApiException(): ApiException {
    throw ApiException(
        statusCode = this.status.value,
        error = try {
            this.body<ErrorTO>()
        } catch (e: Exception) {
            ErrorTO(GENERIC_ERROR, this.body<String>())
        }
    )
}
