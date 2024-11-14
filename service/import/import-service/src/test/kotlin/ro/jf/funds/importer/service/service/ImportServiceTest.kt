package ro.jf.funds.importer.service.service

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.*
import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.event.Producer
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.importer.api.model.ImportTaskTO
import ro.jf.funds.importer.service.domain.ImportTransaction
import ro.jf.funds.importer.service.persistence.ImportTaskRepository
import ro.jf.funds.importer.service.service.parser.ImportParser
import ro.jf.funds.importer.service.service.parser.ImportParserRegistry
import java.util.UUID.randomUUID

class startImportServiceTest {
    private val importParserRegistry = mock<ImportParserRegistry>()
    private val importParser = mock<ImportParser>()
    private val importFundMapper = mock<ImportFundMapper>()
    private val importTaskRepository = mock<ImportTaskRepository>()
    private val createFundTransactionsProducer = mock<Producer<CreateFundTransactionsTO>>()

    private val importService =
        ImportService(importTaskRepository, importParserRegistry, importFundMapper, createFundTransactionsProducer)

    private val userId = randomUUID()
    private val importTaskId = randomUUID()

    @Test
    fun `should produce fund transactions request`(): Unit = runBlocking {
        val importType = ImportFileTypeTO.WALLET_CSV
        val configuration = ImportConfigurationTO(importType, emptyList(), emptyList())
        val importFiles = listOf("fileContent1", "fileContent2")
        whenever(importParserRegistry[importType]).thenReturn(importParser)
        val importItems = mock<List<ImportTransaction>>()
        whenever(importParser.parse(configuration, importFiles)).thenReturn(importItems)
        whenever(importTaskRepository.save(userId, ImportTaskTO.Status.IN_PROGRESS))
            .thenReturn(ImportTaskTO(importTaskId, ImportTaskTO.Status.IN_PROGRESS))
        val fundTransactions = mock<List<CreateFundTransactionTO>>()
        whenever(importFundMapper.mapToFundTransactions(userId, importItems))
            .thenReturn(fundTransactions)

        val importTask = importService.startImport(userId, configuration, importFiles)

        assertThat(importTask.taskId).isEqualTo(importTaskId)
        assertThat(importTask.status).isEqualTo(ImportTaskTO.Status.IN_PROGRESS)

        val eventCaptor = argumentCaptor<Event<CreateFundTransactionsTO>>()
        verify(createFundTransactionsProducer, times(1)).send(eventCaptor.capture())
        assertThat(eventCaptor.firstValue.userId).isEqualTo(userId)
        assertThat(eventCaptor.firstValue.correlationId).isEqualTo(importTaskId)
        assertThat(eventCaptor.firstValue.payload.transactions).isEqualTo(fundTransactions)
        assertThat(eventCaptor.firstValue.key).isEqualTo(userId.toString())
    }

    @Test
    fun `should retrieve failed task on parse exception`(): Unit = runBlocking {
        val importType = ImportFileTypeTO.WALLET_CSV
        val configuration = ImportConfigurationTO(importType, emptyList(), emptyList())
        val importFiles = listOf("fileContent1", "fileContent2")
        whenever(importParserRegistry[importType]).thenReturn(importParser)
        whenever(importParser.parse(configuration, importFiles)).thenThrow(RuntimeException("parse error"))
        whenever(importTaskRepository.save(userId, ImportTaskTO.Status.IN_PROGRESS))
            .thenReturn(ImportTaskTO(importTaskId, ImportTaskTO.Status.IN_PROGRESS))
        whenever(importTaskRepository.update(eq(userId), any<ImportTaskTO>()))
            .thenAnswer { invocation -> invocation.getArgument(1) }

        val importTask = importService.startImport(userId, configuration, importFiles)

        assertThat(importTask.taskId).isEqualTo(importTaskId)
        assertThat(importTask.status).isEqualTo(ImportTaskTO.Status.FAILED)
        assertThat(importTask.reason).isEqualTo("parse error")

        verify(createFundTransactionsProducer, never()).send(any())
    }
}
