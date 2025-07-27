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
import ro.jf.funds.commons.config.configureContentNegotiation
import ro.jf.funds.commons.config.configureDatabaseMigration
import ro.jf.funds.commons.config.configureDependencies
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.commons.test.utils.configureEnvironment
import ro.jf.funds.commons.test.utils.createJsonHttpClient
import ro.jf.funds.commons.test.utils.dbConfig
import ro.jf.funds.commons.test.utils.kafkaConfig
import ro.jf.funds.commons.web.USER_ID_HEADER
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.service.config.configureHistoricalPricingErrorHandling
import ro.jf.funds.historicalpricing.service.config.configureHistoricalPricingRouting
import ro.jf.funds.historicalpricing.service.config.historicalPricingDependencies
import ro.jf.funds.historicalpricing.service.domain.CurrencyPairHistoricalPrice
import ro.jf.funds.historicalpricing.service.persistence.CurrencyPairHistoricalPriceExposedRepository
import ro.jf.funds.historicalpricing.service.service.currency.converter.currencybeacon.CurrencyBeaconCurrencyConverter
import java.util.UUID.randomUUID
import javax.sql.DataSource

@ExtendWith(PostgresContainerExtension::class)
class HistoricalPricingApiTest {
    private val currencyPairHistoricalPriceRepository =
        CurrencyPairHistoricalPriceExposedRepository(PostgresContainerExtension.connection)
    private val currencyConverter = mock<CurrencyBeaconCurrencyConverter>()

    private val userId = randomUUID()
    private val date1 = LocalDate.parse("2025-02-01")
    private val date2 = LocalDate.parse("2025-02-02")
    private val date3 = LocalDate.parse("2025-02-03")
    private val date4 = LocalDate.parse("2025-02-04")

    @Test
    fun `should return valid conversions`() = testApplication {
        configureEnvironment({ testModule() }, dbConfig, kafkaConfig)
        val httpClient = createJsonHttpClient()

        listOf(
            CurrencyPairHistoricalPrice(Currency.RON, Currency.EUR, date1, "0.2".toBigDecimal()),
            CurrencyPairHistoricalPrice(Currency.RON, Currency.EUR, date2, "0.21".toBigDecimal()),
            CurrencyPairHistoricalPrice(Currency.EUR, Currency.RON, date3, "4.9".toBigDecimal())
        ).forEach { currencyPairHistoricalPriceRepository.saveHistoricalPrice(it) }

        val response = httpClient.post("/funds-api/historical-pricing/v1/conversions") {
            header(USER_ID_HEADER, userId.toString())
            contentType(ContentType.Application.Json)
            setBody(
                ConversionsRequest(
                    conversions = listOf(
                        ConversionRequest(Currency.RON, Currency.EUR, date1),
                        ConversionRequest(Currency.RON, Currency.EUR, date2),
                        ConversionRequest(Currency.EUR, Currency.RON, date3)
                    )
                )
            )
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val conversionsResponse = response.body<ConversionsResponse>()
        assertThat(conversionsResponse.conversions).hasSize(3)
        assertThat(conversionsResponse.getRate(Currency.RON, Currency.EUR, date1)).isEqualTo("0.2".toBigDecimal())
        assertThat(conversionsResponse.getRate(Currency.RON, Currency.EUR, date2)).isEqualTo("0.21".toBigDecimal())
        assertThat(conversionsResponse.getRate(Currency.EUR, Currency.RON, date3)).isEqualTo("4.9".toBigDecimal())
        assertThatThrownBy {
            assertThat(conversionsResponse.getRate(Currency.RON, Currency.EUR, date3))
        }
    }

    private fun Application.testModule() {
        val historicalPricingAppTestModule = module {
            single<CurrencyBeaconCurrencyConverter> { currencyConverter }
        }
        configureDependencies(historicalPricingDependencies, historicalPricingAppTestModule)
        configureContentNegotiation()
        configureDatabaseMigration(get<DataSource>())
        configureHistoricalPricingRouting(get())
        configureHistoricalPricingErrorHandling()
    }
}