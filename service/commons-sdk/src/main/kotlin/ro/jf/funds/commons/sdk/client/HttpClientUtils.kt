package ro.jf.funds.commons.sdk.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ro.jf.bk.commons.model.ProblemTO
import ro.jf.bk.commons.model.ResponseTO

fun createHttpClient() = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

suspend inline fun <reified T> HttpResponse.toResponseTO(): ResponseTO<T> {
    return if (this.status.isSuccess()) {
        ResponseTO.Success(this.body<T>())
    } else {
        ResponseTO.Problem(body<ProblemTO>())
    }
}
