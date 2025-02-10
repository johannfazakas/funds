package ro.jf.funds.reporting.service.service.event

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.model.labelsOf
import ro.jf.funds.commons.test.extension.KafkaContainerExtension
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.reporting.api.model.CreateReportViewTO
import ro.jf.funds.reporting.api.model.ReportViewType
import ro.jf.funds.reporting.service.domain.ReportViewTask
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.persistence.ReportViewTaskRepository
import ro.jf.funds.reporting.service.service.ReportViewService
import ro.jf.funds.reporting.service.service.ReportViewTaskService
import ro.jf.funds.reporting.service.utils.record
import ro.jf.funds.reporting.service.utils.transaction
import java.math.BigDecimal
import java.util.UUID.randomUUID

@ExtendWith(PostgresContainerExtension::class)
@ExtendWith(KafkaContainerExtension::class)
class CreateReportViewRequestHandlerTest {

    private val reportViewRepository = ReportViewRepository(PostgresContainerExtension.connection)
    private val reportViewTaskRepository = ReportViewTaskRepository(PostgresContainerExtension.connection)
    private val reportRecordRepository = ReportRecordRepository(PostgresContainerExtension.connection)
    private val fundTransactionSdk = mock<FundTransactionSdk>()
    private val historicalPricingSdk = mock<HistoricalPricingSdk>()

    private val reportViewService = ReportViewService(
        reportViewRepository, reportRecordRepository, fundTransactionSdk, historicalPricingSdk
    )
    private val reportViewTaskService = ReportViewTaskService(reportViewService, reportViewTaskRepository, mock())

    private val requestHandler = CreateReportViewRequestHandler(reportViewTaskService)

    private val userId = randomUUID()
    private val viewName = "Expenses"
    private val fundId = randomUUID()

    private val accountId = randomUUID()
    private val dateTime = LocalDateTime.parse("2021-09-01T12:00:00")

    private val labels = labelsOf("need", "want")

    @Test
    fun `handle create report view request`(): Unit = runBlocking {
        val initialTask = reportViewTaskRepository.create(userId)
        val payload = CreateReportViewTO(viewName, fundId, ReportViewType.EXPENSE, Currency.RON, labels)
        val event = Event(userId, payload, initialTask.taskId)

        val transaction =
            transaction(
                userId, dateTime, listOf(
                    record(fundId, accountId, BigDecimal("100.0"), Currency.RON, labelsOf("need"))
                )
            )
        whenever(fundTransactionSdk.listTransactions(userId, fundId)).thenReturn(ListTO.of(transaction))

        requestHandler.handle(event)

        val completedTask = reportViewTaskRepository.findById(userId, initialTask.taskId) ?: error("Task not found")
        assertThat(completedTask).isInstanceOf(ReportViewTask.Completed::class.java)
        completedTask as ReportViewTask.Completed
        val reportViewId = completedTask.reportViewId

        val reportView = reportViewRepository.findById(userId, reportViewId) ?: error("Report view not found")
        assertThat(reportView.fundId).isEqualTo(fundId)
        assertThat(reportView.name).isEqualTo(viewName)
        assertThat(reportView.type).isEqualTo(ReportViewType.EXPENSE)
    }
}
