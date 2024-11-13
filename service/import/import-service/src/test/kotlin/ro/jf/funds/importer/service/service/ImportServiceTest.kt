package ro.jf.funds.importer.service.service

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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

class ImportServiceTest {
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
    fun `should parse and handle import files`(): Unit = runBlocking {
        val importType = ImportFileTypeTO.WALLET_CSV
        val configuration = ImportConfigurationTO(importType, emptyList(), emptyList())
        val importFiles = listOf("fileContent1", "fileContent2")
        whenever(importParserRegistry[importType]).thenReturn(importParser)
        val importItems = mock<List<ImportTransaction>>()
        whenever(importParser.parse(configuration, importFiles)).thenReturn(importItems)
        whenever(importTaskRepository.save(userId, ImportTaskTO.Status.IN_PROGRESS))
            .thenReturn(ImportTaskTO(importTaskId, ImportTaskTO.Status.IN_PROGRESS))
        val fundTransactions = mock<List<CreateFundTransactionTO>>()
        whenever(importFundMapper.mapToFundTransactions(userId, importTaskId, importItems))
            .thenReturn(fundTransactions)

        importService.import(userId, configuration, importFiles)

        val eventCaptor = argumentCaptor<Event<CreateFundTransactionsTO>>()
        verify(createFundTransactionsProducer, times(1)).send(eventCaptor.capture())
        assertThat(eventCaptor.firstValue.userId).isEqualTo(userId)
        assertThat(eventCaptor.firstValue.correlationId).isEqualTo(importTaskId)
        assertThat(eventCaptor.firstValue.payload.transactions).isEqualTo(fundTransactions)
        assertThat(eventCaptor.firstValue.key).isEqualTo(userId.toString())
    }
}
