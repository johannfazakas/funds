package ro.jf.funds.conversion.service.service

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ro.jf.funds.platform.api.model.Currency
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.Instrument
import ro.jf.funds.conversion.api.model.ConversionRequest
import ro.jf.funds.conversion.api.model.ConversionResponse
import ro.jf.funds.conversion.api.model.ConversionsRequest
import ro.jf.funds.conversion.service.domain.Conversion
import ro.jf.funds.conversion.service.domain.ConversionExceptions
import ro.jf.funds.conversion.service.domain.InstrumentConversionInfo
import ro.jf.funds.conversion.service.domain.InstrumentConversionSource
import ro.jf.funds.conversion.service.persistence.ConversionRepository
import ro.jf.funds.conversion.service.service.currency.CurrencyConverter
import ro.jf.funds.conversion.service.service.instrument.InstrumentConversionInfoRepository
import ro.jf.funds.conversion.service.service.instrument.InstrumentConverter
import ro.jf.funds.conversion.service.service.instrument.InstrumentConverterRegistry

class ConversionServiceTest {
    private val currencyConverter = mock<CurrencyConverter>()
    private val conversionRepository = mock<ConversionRepository>()
    private val instrumentConverterRegistry = mock<InstrumentConverterRegistry>()
    private val instrumentConversionInfoRepository = mock<InstrumentConversionInfoRepository>()

    private val conversionService = ConversionService(
        conversionRepository,
        currencyConverter,
        instrumentConversionInfoRepository,
        instrumentConverterRegistry
    )

    private val date1 = LocalDate.parse("2025-02-01")
    private val date2 = LocalDate.parse("2025-02-02")
    private val date3 = LocalDate.parse("2025-02-03")

    @Nested
    inner class CurrencyConversion {

        @Test
        fun `given stored conversions when converting then should return stored rates`(): Unit = runBlocking {
            whenever(conversionRepository.getConversions(Currency.RON, Currency.EUR, listOf(date1, date2)))
                .thenReturn(
                    listOf(
                        Conversion(Currency.RON, Currency.EUR, date1, BigDecimal.parseString("0.2")),
                        Conversion(Currency.RON, Currency.EUR, date2, BigDecimal.parseString("0.21"))
                    )
                )
            whenever(conversionRepository.getConversions(Currency.EUR, Currency.RON, listOf(date3)))
                .thenReturn(
                    listOf(Conversion(Currency.EUR, Currency.RON, date3, BigDecimal.parseString("4.9")))
                )

            val request = ConversionsRequest(
                listOf(
                    ConversionRequest(Currency.RON, Currency.EUR, date1),
                    ConversionRequest(Currency.RON, Currency.EUR, date2),
                    ConversionRequest(Currency.EUR, Currency.RON, date3)
                )
            )

            val response = conversionService.convert(request)

            assertThat(response.conversions).hasSize(3)
            assertThat(response.getRate(Currency.RON, Currency.EUR, date1)).isEqualTo(BigDecimal.parseString("0.2"))
            assertThat(response.getRate(Currency.RON, Currency.EUR, date2)).isEqualTo(BigDecimal.parseString("0.21"))
            assertThat(response.getRate(Currency.EUR, Currency.RON, date3)).isEqualTo(BigDecimal.parseString("4.9"))
            assertThat(response.getRate(Currency.RON, Currency.EUR, date3)).isNull()
        }

        @Test
        fun `given partially stored conversions when converting then should fetch and store missing rates`(): Unit =
            runBlocking {
                whenever(conversionRepository.getConversions(Currency.RON, Currency.EUR, listOf(date1, date2)))
                    .thenReturn(
                        listOf(Conversion(Currency.RON, Currency.EUR, date1, BigDecimal.parseString("0.2")))
                    )
                whenever(currencyConverter.convert(Currency.RON, Currency.EUR, listOf(date2)))
                    .thenReturn(
                        listOf(ConversionResponse(Currency.RON, Currency.EUR, date2, BigDecimal.parseString("0.21")))
                    )

                val request = ConversionsRequest(
                    listOf(
                        ConversionRequest(Currency.RON, Currency.EUR, date1),
                        ConversionRequest(Currency.RON, Currency.EUR, date2)
                    )
                )

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(2)
                assertThat(response.getRate(Currency.RON, Currency.EUR, date1)).isEqualTo(BigDecimal.parseString("0.2"))
                assertThat(response.getRate(Currency.RON, Currency.EUR, date2)).isEqualTo(BigDecimal.parseString("0.21"))
                verify(conversionRepository).saveConversion(
                    Conversion(Currency.RON, Currency.EUR, date2, BigDecimal.parseString("0.21"))
                )
            }

        @Test
        fun `given converter misses a date when in-memory fallback available then should use previous date rate`(): Unit =
            runBlocking {
                whenever(conversionRepository.getConversions(Currency.EUR, Currency.RON, listOf(date1, date2)))
                    .thenReturn(emptyList())
                whenever(currencyConverter.convert(Currency.EUR, Currency.RON, listOf(date1, date2)))
                    .thenReturn(
                        listOf(ConversionResponse(Currency.EUR, Currency.RON, date1, BigDecimal.parseString("5.0")))
                    )

                val request = ConversionsRequest(
                    listOf(
                        ConversionRequest(Currency.EUR, Currency.RON, date1),
                        ConversionRequest(Currency.EUR, Currency.RON, date2)
                    )
                )

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(2)
                assertThat(response.getRate(Currency.EUR, Currency.RON, date1)).isEqualTo(BigDecimal.parseString("5.0"))
                assertThat(response.getRate(Currency.EUR, Currency.RON, date2)).isEqualTo(BigDecimal.parseString("5.0"))
                verify(conversionRepository, never()).saveConversion(
                    Conversion(Currency.EUR, Currency.RON, date2, BigDecimal.parseString("5.0"))
                )
            }

        @Test
        fun `given converter misses a date when stored fallback exists then should use stored fallback`(): Unit =
            runBlocking {
                val date = LocalDate.parse("2025-02-05")
                whenever(conversionRepository.getConversions(Currency.EUR, Currency.RON, listOf(date)))
                    .thenReturn(emptyList())
                whenever(currencyConverter.convert(Currency.EUR, Currency.RON, listOf(date)))
                    .thenReturn(emptyList())
                whenever(
                    conversionRepository.getConversions(
                        Currency.EUR, Currency.RON, lookbackDates(date)
                    )
                ).thenReturn(
                    listOf(Conversion(Currency.EUR, Currency.RON, date3, BigDecimal.parseString("5.1")))
                )

                val request = ConversionsRequest(listOf(ConversionRequest(Currency.EUR, Currency.RON, date)))

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(1)
                assertThat(response.getRate(Currency.EUR, Currency.RON, date)).isEqualTo(BigDecimal.parseString("5.1"))
                verify(conversionRepository, never()).saveConversion(any())
            }

        @Test
        fun `given converter misses a date when no stored fallback exists then should fetch fallback from converter`(): Unit =
            runBlocking {
                val date = LocalDate.parse("2025-02-05")
                whenever(conversionRepository.getConversions(Currency.EUR, Currency.RON, listOf(date)))
                    .thenReturn(emptyList())
                whenever(currencyConverter.convert(Currency.EUR, Currency.RON, listOf(date)))
                    .thenReturn(emptyList())
                whenever(
                    conversionRepository.getConversions(Currency.EUR, Currency.RON, lookbackDates(date))
                ).thenReturn(emptyList())
                whenever(
                    currencyConverter.convert(Currency.EUR, Currency.RON, lookbackDates(date))
                ).thenReturn(
                    listOf(
                        ConversionResponse(
                            Currency.EUR, Currency.RON,
                            LocalDate.parse("2025-02-03"),
                            BigDecimal.parseString("5.1")
                        ),
                        ConversionResponse(
                            Currency.EUR, Currency.RON,
                            LocalDate.parse("2025-01-25"),
                            BigDecimal.parseString("4.9")
                        )
                    )
                )

                val request = ConversionsRequest(listOf(ConversionRequest(Currency.EUR, Currency.RON, date)))

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(1)
                assertThat(response.getRate(Currency.EUR, Currency.RON, date)).isEqualTo(BigDecimal.parseString("5.1"))
                verify(conversionRepository, never()).saveConversion(
                    Conversion(Currency.EUR, Currency.RON, date, BigDecimal.parseString("5.1"))
                )
            }

        @Test
        fun `given converter misses a date when no fallback within lookback then should throw error`(): Unit =
            runBlocking {
                val date = LocalDate.parse("2025-02-15")
                whenever(conversionRepository.getConversions(Currency.EUR, Currency.RON, listOf(date)))
                    .thenReturn(emptyList())
                whenever(currencyConverter.convert(Currency.EUR, Currency.RON, listOf(date)))
                    .thenReturn(emptyList())
                whenever(
                    conversionRepository.getConversions(Currency.EUR, Currency.RON, lookbackDates(date))
                ).thenReturn(emptyList())
                whenever(
                    currencyConverter.convert(Currency.EUR, Currency.RON, lookbackDates(date))
                ).thenReturn(emptyList())

                val request = ConversionsRequest(listOf(ConversionRequest(Currency.EUR, Currency.RON, date)))

                assertThatThrownBy { runBlocking { conversionService.convert(request) } }
                    .isInstanceOf(ConversionExceptions.ConversionNotFound::class.java)
            }
    }

    @Nested
    inner class InstrumentToMainCurrencyConversion {
        private val instrument = Instrument("VWCE")
        private val pricingInstrument = InstrumentConversionInfo(
            instrument = instrument,
            source = InstrumentConversionSource.FINANCIAL_TIMES,
            symbol = "VWCE",
            mainCurrency = Currency.EUR
        )
        private val instrumentConverter = mock<InstrumentConverter>()

        private fun setupInstrumentMocks() = runBlocking {
            whenever(instrumentConversionInfoRepository.findByInstrument(instrument))
                .thenReturn(pricingInstrument)
            whenever(instrumentConverterRegistry.getConverter(pricingInstrument))
                .thenReturn(instrumentConverter)
        }

        @Test
        fun `given stored conversions when converting then should return stored rates`(): Unit = runBlocking {
            setupInstrumentMocks()
            whenever(conversionRepository.getConversions(instrument, Currency.EUR, listOf(date1, date2)))
                .thenReturn(
                    listOf(
                        Conversion(instrument, Currency.EUR, date1, BigDecimal.parseString("100.5")),
                        Conversion(instrument, Currency.EUR, date2, BigDecimal.parseString("101.2"))
                    )
                )

            val request = ConversionsRequest(
                listOf(
                    ConversionRequest(instrument, Currency.EUR, date1),
                    ConversionRequest(instrument, Currency.EUR, date2)
                )
            )

            val response = conversionService.convert(request)

            assertThat(response.conversions).hasSize(2)
            assertThat(response.getRate(instrument, Currency.EUR, date1)).isEqualTo(BigDecimal.parseString("100.5"))
            assertThat(response.getRate(instrument, Currency.EUR, date2)).isEqualTo(BigDecimal.parseString("101.2"))
        }

        @Test
        fun `given partially stored conversions when converting then should fetch and store missing rates`(): Unit =
            runBlocking {
                setupInstrumentMocks()
                whenever(conversionRepository.getConversions(instrument, Currency.EUR, listOf(date1, date2, date3)))
                    .thenReturn(
                        listOf(Conversion(instrument, Currency.EUR, date1, BigDecimal.parseString("100.5")))
                    )
                whenever(instrumentConverter.convert(pricingInstrument, listOf(date2, date3)))
                    .thenReturn(
                        listOf(
                            ConversionResponse(instrument, Currency.EUR, date2, BigDecimal.parseString("101.2")),
                            ConversionResponse(instrument, Currency.EUR, date3, BigDecimal.parseString("102.8"))
                        )
                    )

                val request = ConversionsRequest(
                    listOf(
                        ConversionRequest(instrument, Currency.EUR, date1),
                        ConversionRequest(instrument, Currency.EUR, date2),
                        ConversionRequest(instrument, Currency.EUR, date3)
                    )
                )

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(3)
                assertThat(response.getRate(instrument, Currency.EUR, date1))
                    .isEqualTo(BigDecimal.parseString("100.5"))
                assertThat(response.getRate(instrument, Currency.EUR, date2))
                    .isEqualTo(BigDecimal.parseString("101.2"))
                assertThat(response.getRate(instrument, Currency.EUR, date3))
                    .isEqualTo(BigDecimal.parseString("102.8"))
                verify(conversionRepository).saveConversion(
                    Conversion(instrument, Currency.EUR, date2, BigDecimal.parseString("101.2"))
                )
                verify(conversionRepository).saveConversion(
                    Conversion(instrument, Currency.EUR, date3, BigDecimal.parseString("102.8"))
                )
            }

        @Test
        fun `given converter misses a date when in-memory fallback available then should use previous date rate`(): Unit =
            runBlocking {
                setupInstrumentMocks()
                whenever(conversionRepository.getConversions(instrument, Currency.EUR, listOf(date1, date2)))
                    .thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, listOf(date1, date2)))
                    .thenReturn(
                        listOf(ConversionResponse(instrument, Currency.EUR, date1, BigDecimal.parseString("100.5")))
                    )

                val request = ConversionsRequest(
                    listOf(
                        ConversionRequest(instrument, Currency.EUR, date1),
                        ConversionRequest(instrument, Currency.EUR, date2)
                    )
                )

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(2)
                assertThat(response.getRate(instrument, Currency.EUR, date1))
                    .isEqualTo(BigDecimal.parseString("100.5"))
                assertThat(response.getRate(instrument, Currency.EUR, date2))
                    .isEqualTo(BigDecimal.parseString("100.5"))
                verify(conversionRepository, never()).saveConversion(
                    Conversion(instrument, Currency.EUR, date2, BigDecimal.parseString("100.5"))
                )
            }

        @Test
        fun `given converter misses a date when stored fallback exists then should use stored fallback`(): Unit =
            runBlocking {
                setupInstrumentMocks()
                val date = LocalDate.parse("2025-02-05")
                whenever(conversionRepository.getConversions(instrument, Currency.EUR, listOf(date)))
                    .thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, listOf(date)))
                    .thenReturn(emptyList())
                whenever(
                    conversionRepository.getConversions(instrument, Currency.EUR, lookbackDates(date))
                ).thenReturn(
                    listOf(Conversion(instrument, Currency.EUR, date3, BigDecimal.parseString("102.8")))
                )

                val request = ConversionsRequest(listOf(ConversionRequest(instrument, Currency.EUR, date)))

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(1)
                assertThat(response.getRate(instrument, Currency.EUR, date))
                    .isEqualTo(BigDecimal.parseString("102.8"))
                verify(conversionRepository, never()).saveConversion(any())
            }

        @Test
        fun `given converter misses a date when no stored fallback exists then should fetch fallback from converter`(): Unit =
            runBlocking {
                setupInstrumentMocks()
                val date = LocalDate.parse("2025-02-05")
                whenever(conversionRepository.getConversions(instrument, Currency.EUR, listOf(date)))
                    .thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, listOf(date)))
                    .thenReturn(emptyList())
                whenever(
                    conversionRepository.getConversions(instrument, Currency.EUR, lookbackDates(date))
                ).thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, lookbackDates(date)))
                    .thenReturn(
                        listOf(
                            ConversionResponse(
                                instrument, Currency.EUR,
                                LocalDate.parse("2025-02-03"),
                                BigDecimal.parseString("102.8")
                            ),
                            ConversionResponse(
                                instrument, Currency.EUR,
                                LocalDate.parse("2025-01-25"),
                                BigDecimal.parseString("99.5")
                            )
                        )
                    )

                val request = ConversionsRequest(listOf(ConversionRequest(instrument, Currency.EUR, date)))

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(1)
                assertThat(response.getRate(instrument, Currency.EUR, date))
                    .isEqualTo(BigDecimal.parseString("102.8"))
                verify(conversionRepository, never()).saveConversion(
                    Conversion(instrument, Currency.EUR, date, BigDecimal.parseString("102.8"))
                )
            }

        @Test
        fun `given converter misses a date when no fallback within lookback then should throw error`(): Unit =
            runBlocking {
                setupInstrumentMocks()
                val date = LocalDate.parse("2025-02-15")
                whenever(conversionRepository.getConversions(instrument, Currency.EUR, listOf(date)))
                    .thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, listOf(date)))
                    .thenReturn(emptyList())
                whenever(
                    conversionRepository.getConversions(instrument, Currency.EUR, lookbackDates(date))
                ).thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, lookbackDates(date)))
                    .thenReturn(emptyList())

                val request = ConversionsRequest(listOf(ConversionRequest(instrument, Currency.EUR, date)))

                assertThatThrownBy { runBlocking { conversionService.convert(request) } }
                    .isInstanceOf(ConversionExceptions.ConversionNotFound::class.java)
            }
    }

    @Nested
    inner class InstrumentToImplicitCurrencyConversion {
        private val instrument = Instrument("IMAE")
        private val pricingInstrument = InstrumentConversionInfo(
            instrument = instrument,
            source = InstrumentConversionSource.FINANCIAL_TIMES,
            symbol = "IMAE",
            mainCurrency = Currency.EUR
        )
        private val instrumentConverter = mock<InstrumentConverter>()

        private fun setupInstrumentMocks() = runBlocking {
            whenever(instrumentConversionInfoRepository.findByInstrument(instrument))
                .thenReturn(pricingInstrument)
            whenever(instrumentConverterRegistry.getConverter(pricingInstrument))
                .thenReturn(instrumentConverter)
        }

        @Test
        fun `given stored composite conversions when converting then should return stored rates`(): Unit =
            runBlocking {
                setupInstrumentMocks()
                whenever(conversionRepository.getConversions(instrument, Currency.RON, listOf(date1, date2)))
                    .thenReturn(
                        listOf(
                            Conversion(instrument, Currency.RON, date1, BigDecimal.parseString("502.5")),
                            Conversion(instrument, Currency.RON, date2, BigDecimal.parseString("505.0"))
                        )
                    )

                val request = ConversionsRequest(
                    listOf(
                        ConversionRequest(instrument, Currency.RON, date1),
                        ConversionRequest(instrument, Currency.RON, date2)
                    )
                )

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(2)
                assertThat(response.getRate(instrument, Currency.RON, date1))
                    .isEqualTo(BigDecimal.parseString("502.5"))
                assertThat(response.getRate(instrument, Currency.RON, date2))
                    .isEqualTo(BigDecimal.parseString("505.0"))
            }

        @Test
        fun `given no stored conversions when converting then should fetch both legs and store all intermediary and composite conversions`(): Unit =
            runBlocking {
                setupInstrumentMocks()
                whenever(conversionRepository.getConversions(instrument, Currency.RON, listOf(date1)))
                    .thenReturn(emptyList())
                whenever(conversionRepository.getConversions(instrument, Currency.EUR, listOf(date1)))
                    .thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, listOf(date1)))
                    .thenReturn(
                        listOf(ConversionResponse(instrument, Currency.EUR, date1, BigDecimal.parseString("91.5")))
                    )
                whenever(conversionRepository.getConversions(Currency.EUR, Currency.RON, listOf(date1)))
                    .thenReturn(emptyList())
                whenever(currencyConverter.convert(Currency.EUR, Currency.RON, listOf(date1)))
                    .thenReturn(
                        listOf(ConversionResponse(Currency.EUR, Currency.RON, date1, BigDecimal.parseString("5.0")))
                    )

                val request = ConversionsRequest(
                    listOf(ConversionRequest(instrument, Currency.RON, date1))
                )

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(1)
                assertThat(response.getRate(instrument, Currency.RON, date1))
                    .isEqualTo(BigDecimal.parseString("457.50"))
                verify(conversionRepository).saveConversion(
                    Conversion(instrument, Currency.EUR, date1, BigDecimal.parseString("91.5"))
                )
                verify(conversionRepository).saveConversion(
                    Conversion(Currency.EUR, Currency.RON, date1, BigDecimal.parseString("5.0"))
                )
                verify(conversionRepository).saveConversion(
                    Conversion(instrument, Currency.RON, date1, BigDecimal.parseString("457.50"))
                )
            }

        @Test
        fun `given partially stored composites when converting then should only fetch missing dates`(): Unit =
            runBlocking {
                setupInstrumentMocks()
                whenever(conversionRepository.getConversions(instrument, Currency.RON, listOf(date1, date2)))
                    .thenReturn(
                        listOf(Conversion(instrument, Currency.RON, date1, BigDecimal.parseString("502.5")))
                    )
                whenever(conversionRepository.getConversions(instrument, Currency.EUR, listOf(date2)))
                    .thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, listOf(date2)))
                    .thenReturn(
                        listOf(ConversionResponse(instrument, Currency.EUR, date2, BigDecimal.parseString("101.0")))
                    )
                whenever(conversionRepository.getConversions(Currency.EUR, Currency.RON, listOf(date2)))
                    .thenReturn(emptyList())
                whenever(currencyConverter.convert(Currency.EUR, Currency.RON, listOf(date2)))
                    .thenReturn(
                        listOf(ConversionResponse(Currency.EUR, Currency.RON, date2, BigDecimal.parseString("5.0")))
                    )

                val request = ConversionsRequest(
                    listOf(
                        ConversionRequest(instrument, Currency.RON, date1),
                        ConversionRequest(instrument, Currency.RON, date2)
                    )
                )

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(2)
                assertThat(response.getRate(instrument, Currency.RON, date1))
                    .isEqualTo(BigDecimal.parseString("502.5"))
                assertThat(response.getRate(instrument, Currency.RON, date2))
                    .isEqualTo(BigDecimal.parseString("505.00"))
            }

        @Test
        fun `given instrument leg misses a date when in-memory fallback available then should use per-leg fallback`(): Unit =
            runBlocking {
                setupInstrumentMocks()
                whenever(conversionRepository.getConversions(instrument, Currency.RON, listOf(date1, date2)))
                    .thenReturn(emptyList())
                whenever(conversionRepository.getConversions(instrument, Currency.EUR, listOf(date1, date2)))
                    .thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, listOf(date1, date2)))
                    .thenReturn(
                        listOf(ConversionResponse(instrument, Currency.EUR, date1, BigDecimal.parseString("91.5")))
                    )
                whenever(conversionRepository.getConversions(Currency.EUR, Currency.RON, listOf(date1, date2)))
                    .thenReturn(emptyList())
                whenever(currencyConverter.convert(Currency.EUR, Currency.RON, listOf(date1, date2)))
                    .thenReturn(
                        listOf(
                            ConversionResponse(Currency.EUR, Currency.RON, date1, BigDecimal.parseString("5.0")),
                            ConversionResponse(Currency.EUR, Currency.RON, date2, BigDecimal.parseString("5.1"))
                        )
                    )

                val request = ConversionsRequest(
                    listOf(
                        ConversionRequest(instrument, Currency.RON, date1),
                        ConversionRequest(instrument, Currency.RON, date2)
                    )
                )

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(2)
                assertThat(response.getRate(instrument, Currency.RON, date1))
                    .isEqualTo(BigDecimal.parseString("457.50"))
                assertThat(response.getRate(instrument, Currency.RON, date2))
                    .isEqualTo(BigDecimal.parseString("466.65"))
                verify(conversionRepository, never()).saveConversion(
                    Conversion(instrument, Currency.RON, date2, BigDecimal.parseString("466.65"))
                )
            }

        @Test
        fun `given instrument leg misses a date when no stored fallback then should fetch fallback from converter`(): Unit =
            runBlocking {
                setupInstrumentMocks()
                val date = LocalDate.parse("2025-02-05")
                whenever(conversionRepository.getConversions(instrument, Currency.RON, listOf(date)))
                    .thenReturn(emptyList())
                whenever(conversionRepository.getConversions(instrument, Currency.EUR, listOf(date)))
                    .thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, listOf(date)))
                    .thenReturn(emptyList())
                whenever(
                    conversionRepository.getConversions(instrument, Currency.EUR, lookbackDates(date))
                ).thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, lookbackDates(date)))
                    .thenReturn(
                        listOf(
                            ConversionResponse(
                                instrument, Currency.EUR,
                                LocalDate.parse("2025-02-03"),
                                BigDecimal.parseString("91.5")
                            ),
                            ConversionResponse(
                                instrument, Currency.EUR,
                                LocalDate.parse("2025-01-25"),
                                BigDecimal.parseString("88.0")
                            )
                        )
                    )
                whenever(conversionRepository.getConversions(Currency.EUR, Currency.RON, listOf(date)))
                    .thenReturn(emptyList())
                whenever(currencyConverter.convert(Currency.EUR, Currency.RON, listOf(date)))
                    .thenReturn(
                        listOf(ConversionResponse(Currency.EUR, Currency.RON, date, BigDecimal.parseString("5.0")))
                    )

                val request = ConversionsRequest(listOf(ConversionRequest(instrument, Currency.RON, date)))

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(1)
                assertThat(response.getRate(instrument, Currency.RON, date))
                    .isEqualTo(BigDecimal.parseString("457.50"))
                verify(conversionRepository, never()).saveConversion(
                    Conversion(instrument, Currency.RON, date, BigDecimal.parseString("457.50"))
                )
            }

        @Test
        fun `given currency leg misses a date when in-memory fallback available then should use per-leg fallback`(): Unit =
            runBlocking {
                setupInstrumentMocks()
                whenever(conversionRepository.getConversions(instrument, Currency.RON, listOf(date1, date2)))
                    .thenReturn(emptyList())
                whenever(conversionRepository.getConversions(instrument, Currency.EUR, listOf(date1, date2)))
                    .thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, listOf(date1, date2)))
                    .thenReturn(
                        listOf(
                            ConversionResponse(instrument, Currency.EUR, date1, BigDecimal.parseString("91.5")),
                            ConversionResponse(instrument, Currency.EUR, date2, BigDecimal.parseString("92.0"))
                        )
                    )
                whenever(conversionRepository.getConversions(Currency.EUR, Currency.RON, listOf(date1, date2)))
                    .thenReturn(emptyList())
                whenever(currencyConverter.convert(Currency.EUR, Currency.RON, listOf(date1, date2)))
                    .thenReturn(
                        listOf(ConversionResponse(Currency.EUR, Currency.RON, date1, BigDecimal.parseString("5.0")))
                    )

                val request = ConversionsRequest(
                    listOf(
                        ConversionRequest(instrument, Currency.RON, date1),
                        ConversionRequest(instrument, Currency.RON, date2)
                    )
                )

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(2)
                assertThat(response.getRate(instrument, Currency.RON, date1))
                    .isEqualTo(BigDecimal.parseString("457.50"))
                assertThat(response.getRate(instrument, Currency.RON, date2))
                    .isEqualTo(BigDecimal.parseString("460.00"))
                verify(conversionRepository, never()).saveConversion(
                    Conversion(instrument, Currency.RON, date2, BigDecimal.parseString("460.00"))
                )
            }

        @Test
        fun `given both legs miss a date when in-memory fallback available then should apply per-leg fallback on both`(): Unit =
            runBlocking {
                setupInstrumentMocks()
                whenever(conversionRepository.getConversions(instrument, Currency.RON, listOf(date1, date2)))
                    .thenReturn(emptyList())
                whenever(conversionRepository.getConversions(instrument, Currency.EUR, listOf(date1, date2)))
                    .thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, listOf(date1, date2)))
                    .thenReturn(
                        listOf(ConversionResponse(instrument, Currency.EUR, date1, BigDecimal.parseString("91.5")))
                    )
                whenever(conversionRepository.getConversions(Currency.EUR, Currency.RON, listOf(date1, date2)))
                    .thenReturn(emptyList())
                whenever(currencyConverter.convert(Currency.EUR, Currency.RON, listOf(date1, date2)))
                    .thenReturn(
                        listOf(ConversionResponse(Currency.EUR, Currency.RON, date1, BigDecimal.parseString("5.0")))
                    )

                val request = ConversionsRequest(
                    listOf(
                        ConversionRequest(instrument, Currency.RON, date1),
                        ConversionRequest(instrument, Currency.RON, date2)
                    )
                )

                val response = conversionService.convert(request)

                assertThat(response.conversions).hasSize(2)
                assertThat(response.getRate(instrument, Currency.RON, date1))
                    .isEqualTo(BigDecimal.parseString("457.50"))
                assertThat(response.getRate(instrument, Currency.RON, date2))
                    .isEqualTo(BigDecimal.parseString("457.50"))
                verify(conversionRepository, never()).saveConversion(
                    Conversion(instrument, Currency.RON, date2, BigDecimal.parseString("457.50"))
                )
            }

        @Test
        fun `given fallback used when storing then should not store fallback-derived composites`(): Unit =
            runBlocking {
                setupInstrumentMocks()
                whenever(conversionRepository.getConversions(instrument, Currency.RON, listOf(date1, date2)))
                    .thenReturn(emptyList())
                whenever(conversionRepository.getConversions(instrument, Currency.EUR, listOf(date1, date2)))
                    .thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, listOf(date1, date2)))
                    .thenReturn(
                        listOf(ConversionResponse(instrument, Currency.EUR, date1, BigDecimal.parseString("91.5")))
                    )
                whenever(conversionRepository.getConversions(Currency.EUR, Currency.RON, listOf(date1, date2)))
                    .thenReturn(emptyList())
                whenever(currencyConverter.convert(Currency.EUR, Currency.RON, listOf(date1, date2)))
                    .thenReturn(
                        listOf(
                            ConversionResponse(Currency.EUR, Currency.RON, date1, BigDecimal.parseString("5.0")),
                            ConversionResponse(Currency.EUR, Currency.RON, date2, BigDecimal.parseString("5.1"))
                        )
                    )

                val request = ConversionsRequest(
                    listOf(
                        ConversionRequest(instrument, Currency.RON, date1),
                        ConversionRequest(instrument, Currency.RON, date2)
                    )
                )

                conversionService.convert(request)

                verify(conversionRepository).saveConversion(
                    Conversion(instrument, Currency.EUR, date1, BigDecimal.parseString("91.5"))
                )
                verify(conversionRepository).saveConversion(
                    Conversion(Currency.EUR, Currency.RON, date1, BigDecimal.parseString("5.0"))
                )
                verify(conversionRepository).saveConversion(
                    Conversion(Currency.EUR, Currency.RON, date2, BigDecimal.parseString("5.1"))
                )
                verify(conversionRepository).saveConversion(
                    Conversion(instrument, Currency.RON, date1, BigDecimal.parseString("457.50"))
                )
                verify(conversionRepository, never()).saveConversion(
                    Conversion(instrument, Currency.RON, date2, BigDecimal.parseString("466.65"))
                )
            }

        @Test
        fun `given instrument leg has no fallback within lookback then should throw error`(): Unit =
            runBlocking {
                setupInstrumentMocks()
                val date = LocalDate.parse("2025-02-15")
                whenever(conversionRepository.getConversions(instrument, Currency.RON, listOf(date)))
                    .thenReturn(emptyList())
                whenever(conversionRepository.getConversions(instrument, Currency.EUR, listOf(date)))
                    .thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, listOf(date)))
                    .thenReturn(emptyList())
                whenever(
                    conversionRepository.getConversions(instrument, Currency.EUR, lookbackDates(date))
                ).thenReturn(emptyList())
                whenever(instrumentConverter.convert(pricingInstrument, lookbackDates(date)))
                    .thenReturn(emptyList())

                val request = ConversionsRequest(listOf(ConversionRequest(instrument, Currency.RON, date)))

                assertThatThrownBy { runBlocking { conversionService.convert(request) } }
                    .isInstanceOf(ConversionExceptions.ConversionNotFound::class.java)
            }
    }

    companion object {
        private const val MAX_FALLBACK_DAYS = 15

        private fun lookbackDates(date: LocalDate): List<LocalDate> =
            (1..MAX_FALLBACK_DAYS).map { date.minus(it, DateTimeUnit.DAY) }
    }
}
