package ro.jf.funds.reporting.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import ro.jf.funds.commons.model.Currency.Companion.EUR
import ro.jf.funds.commons.model.Currency.Companion.RON
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.model.labelsOf
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.reporting.api.model.*
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.utils.record
import ro.jf.funds.reporting.service.utils.transaction
import java.math.BigDecimal
import java.util.UUID.randomUUID

class ReportViewServiceTest {

    private val reportViewRepository = mock<ReportViewRepository>()
    private val reportRecordRepository = mock<ReportRecordRepository>()
    private val fundTransactionSdk = mock<FundTransactionSdk>()
    private val historicalPricingSdk = mock<HistoricalPricingSdk>()

    private val reportViewService =
        ReportViewService(reportViewRepository, reportRecordRepository, fundTransactionSdk, historicalPricingSdk)

    private val userId = randomUUID()
    private val reportViewId = randomUUID()
    private val reportViewName = "view name"
    private val expensesFundId = randomUUID()
    private val bankAccountId = randomUUID()
    private val cashAccountId = randomUUID()
    private val dateTime1 = LocalDateTime.parse("2021-09-01T12:00:00")
    private val dateTime2 = LocalDateTime.parse("2021-09-03T12:00:00")
    private val allLabels = labelsOf("need", "want")

    @Test
    fun `create report view should create report view`(): Unit = runBlocking {
        val request =
            CreateReportViewTO(reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels, emptyList())
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)
        whenever(
            reportViewRepository.save(
                userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
            )
        )
            .thenReturn(
                ReportView(
                    reportViewId, userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
                )
            )
        whenever(fundTransactionSdk.listTransactions(userId, expensesFundId)).thenReturn(ListTO.of())

        val reportView = reportViewService.createReportView(userId, request)

        assertThat(reportView.id).isEqualTo(reportViewId)
        assertThat(reportView.userId).isEqualTo(userId)
        assertThat(reportView.name).isEqualTo(reportViewName)
        assertThat(reportView.fundId).isEqualTo(expensesFundId)
        assertThat(reportView.type).isEqualTo(ReportViewType.EXPENSE)

        verify(reportViewRepository, times(1))
            .save(userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels)
    }

    @Test
    fun `create report view should store single fund report records`(): Unit = runBlocking {
        val request =
            CreateReportViewTO(reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels, emptyList())
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)
        whenever(
            reportViewRepository.save(
                userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
            )
        )
            .thenReturn(
                ReportView(
                    reportViewId, userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
                )
            )

        val transaction1 =
            transaction(
                userId, dateTime1, listOf(
                    record(expensesFundId, bankAccountId, BigDecimal("100.0"), RON, labelsOf("need"))
                )
            )
        val transaction2 =
            transaction(
                userId, dateTime2, listOf(
                    record(expensesFundId, cashAccountId, BigDecimal("-200.0"), RON, labelsOf("want"))
                )
            )
        val transaction3 =
            transaction(
                userId, dateTime2, listOf(
                    record(expensesFundId, cashAccountId, BigDecimal("-20.0"), RON, labelsOf("other"))
                )
            )
        whenever(fundTransactionSdk.listTransactions(userId, expensesFundId))
            .thenReturn(ListTO.of(transaction1, transaction2, transaction3))

        val reportView = reportViewService.createReportView(userId, request)

        val commandCaptor = argumentCaptor<List<CreateReportRecordCommand>>()
        verify(reportRecordRepository, times(1)).saveAll(commandCaptor.capture())
        assertThat(commandCaptor.firstValue).containsExactlyInAnyOrder(
            CreateReportRecordCommand(
                userId, reportView.id, dateTime1.date, RON,
                BigDecimal("100.0"), BigDecimal("100.0"), labelsOf("need")
            ),
            CreateReportRecordCommand(
                userId, reportView.id, dateTime2.date, RON,
                BigDecimal("-200.0"), BigDecimal("-200.0"), labelsOf("want")
            ),
            CreateReportRecordCommand(
                userId, reportView.id, dateTime2.date, RON,
                BigDecimal("-20.0"), BigDecimal("-20.0"), labelsOf("other")
            )
        )
    }

    @Test
    fun `create report view should store single fund report records with conversions`(): Unit = runBlocking {
        val request =
            CreateReportViewTO(reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels, emptyList())
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)
        whenever(
            reportViewRepository.save(
                userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
            )
        )
            .thenReturn(
                ReportView(
                    reportViewId, userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
                )
            )

        val transaction1 =
            transaction(
                userId, dateTime1, listOf(
                    record(expensesFundId, bankAccountId, BigDecimal("100.0"), EUR, labelsOf("need"))
                )
            )
        whenever(fundTransactionSdk.listTransactions(userId, expensesFundId)).thenReturn(ListTO.of(transaction1))
        val conversionsRequest = ConversionsRequest(
            listOf(ConversionRequest(EUR, RON, dateTime1.date))
        )
        whenever(historicalPricingSdk.convert(userId, conversionsRequest))
            .thenReturn(ConversionsResponse(listOf(ConversionResponse(EUR, RON, dateTime1.date, BigDecimal("5.0")))))

        val reportView = reportViewService.createReportView(userId, request)

        val commandCaptor = argumentCaptor<List<CreateReportRecordCommand>>()
        verify(reportRecordRepository, times(1)).saveAll(commandCaptor.capture())
        assertThat(commandCaptor.firstValue).containsExactlyInAnyOrder(
            CreateReportRecordCommand(
                userId, reportView.id, dateTime1.date, EUR,
                BigDecimal("100.0"), BigDecimal("500.00"), labelsOf("need")
            ),
        )
    }

    @Test
    fun `create report view with same name should raise error`(): Unit = runBlocking {
        val request =
            CreateReportViewTO(reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels, emptyList())
        whenever(reportViewRepository.findByName(userId, reportViewName))
            .thenReturn(
                ReportView(
                    reportViewId, userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
                )
            )

        assertThatThrownBy { runBlocking { reportViewService.createReportView(userId, request) } }
            .isInstanceOf(ReportingException.ReportViewAlreadyExists::class.java)

        verify(reportViewRepository, never()).save(
            userId, reportViewName, expensesFundId, ReportViewType.EXPENSE, RON, allLabels
        )
    }
}
