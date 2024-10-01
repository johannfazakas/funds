package ro.jf.funds.importer.service

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ro.jf.bk.commons.web.USER_ID_HEADER
import ro.jf.funds.importer.api.model.*
import java.io.File
import java.util.*
import java.util.UUID.randomUUID

class ImportApiTest {
    @Test
    fun `test import`() = testApplication {
        configureEnvironment()

        val httpClient = createJsonHttpClient()
        val userId = randomUUID()
        val csvFile = File("src/test/resources/data/csv-v2.csv")
        val importConfiguration = ImportConfigurationTO(
            keys = KeysTO(
                accountName = "cont",
                currency = "valuta",
                amount = "suma",
                date = "data"
            ),
            formatting = FormattingTO(
                csvDelimiter = ";",
                dateTimeFormat = "yyyy-MM-dd'T'HH:mm",
                locale = Locale.FRENCH
            ),
            transactionIdentification = TransactionIdentificationTO(
                conditions = listOf(
                    TransactionConditionTO.ColumnEquality("tranzactie"),
                    TransactionConditionTO.ColumnConstant("tip", "TRANSFER"),
                    TransactionConditionTO.NumericalAbsoluteEquality("suma")
                )
            )
        )

        val response = httpClient.post("/bk-api/import/v1/imports") {
            header(USER_ID_HEADER, userId.toString())
            setBody(MultiPartFormDataContent(
                formData {
                    append("file", csvFile.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Text.CSV)
                        append(HttpHeaders.ContentDisposition, "filename=\"${csvFile.name}\"")
                    })
                    append("configuration", Json.encodeToString(importConfiguration), Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                    })
                }
            ))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val responseBody = response.body<ImportResponse>()
        assertThat(responseBody.response).isNotEmpty()
    }

    private fun ApplicationTestBuilder.configureEnvironment() {
        environment {
            config = MapApplicationConfig()
        }
        application {
            module()
        }
    }

    private fun ApplicationTestBuilder.createJsonHttpClient() =
        createClient { install(ContentNegotiation) { json() } }
}
