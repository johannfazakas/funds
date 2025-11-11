package ro.jf.funds.historicalpricing.service.web

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.model.Currency.Companion.EUR
import ro.jf.funds.commons.model.Currency.Companion.RON
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.configureEnvironment
import ro.jf.funds.commons.test.utils.createJsonHttpClient
import ro.jf.funds.commons.test.utils.dbConfig
import ro.jf.funds.commons.test.utils.kafkaConfig
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.service.config.configureConversionErrorHandling
import ro.jf.funds.historicalpricing.service.config.configureConversionRouting
import ro.jf.funds.historicalpricing.service.config.conversionDependencies
import ro.jf.funds.historicalpricing.service.domain.Conversion
import ro.jf.funds.historicalpricing.service.persistence.ConversionRepository
import ro.jf.funds.historicalpricing.service.service.currency.converter.currencybeacon.CurrencyBeaconCurrencyConverter
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
class ConversionApiTest {
    private val conversionRepository =
        ConversionRepository(PostgresContainerExtension.connection)
    private val currencyConverter = mock<CurrencyBeaconCurrencyConverter>()

    private val date1 = LocalDate.parse("2025-02-01")
    private val date2 = LocalDate.parse("2025-02-02")
    private val date3 = LocalDate.parse("2025-02-03")
    private val date4 = LocalDate.parse("2025-02-04")

    @Test
    fun `should return valid conversions`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()

        listOf(
            Conversion(RON, EUR, date1, "0.2".toBigDecimal()),
            Conversion(RON, EUR, date2, "0.21".toBigDecimal()),
            Conversion(EUR, RON, date3, "4.9".toBigDecimal())
        ).forEach { conversionRepository.saveHistoricalPrice(it) }

        whenever(currencyConverter.convert(EUR, RON, listOf(date4)))
            .thenReturn(
                listOf(
                    ConversionResponse(EUR, RON, date4, "4.92".toBigDecimal()),
                )
            )

        val response = httpClient.post("/funds-api/historical-pricing/v1/conversions") {
            contentType(ContentType.Application.Json)
            setBody(
                ConversionsRequest(
                    conversions = listOf(
                        ConversionRequest(RON, EUR, date1),
                        ConversionRequest(RON, EUR, date2),
                        ConversionRequest(EUR, RON, date3),
                        ConversionRequest(EUR, RON, date4),
                    )
                )
            )
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val conversionsResponse = response.body<ConversionsResponse>()
        assertThat(conversionsResponse.conversions).hasSize(4)
        assertThat(conversionsResponse.getRate(RON, EUR, date1)).isEqualTo("0.2".toBigDecimal())
        assertThat(conversionsResponse.getRate(RON, EUR, date2)).isEqualTo("0.21".toBigDecimal())
        assertThat(conversionsResponse.getRate(EUR, RON, date3)).isEqualTo("4.9".toBigDecimal())
        assertThat(conversionsResponse.getRate(EUR, RON, date4)).isEqualTo("4.92".toBigDecimal())
        assertThatThrownBy {
            assertThat(conversionsResponse.getRate(RON, EUR, date3))
        }
    }

    private fun Application.testModule() {
        val conversionAppTestModule = module {
            single<CurrencyBeaconCurrencyConverter> { currencyConverter }
        }
        configureDependencies(conversionDependencies, conversionAppTestModule)
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureConversionRouting(get())
        configureConversionErrorHandling()
    }
}