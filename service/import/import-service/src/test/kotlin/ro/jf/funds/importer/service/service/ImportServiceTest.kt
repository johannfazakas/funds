package ro.jf.funds.importer.service.service

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.*
import ro.jf.funds.platform.jvm.event.Event
import ro.jf.funds.platform.jvm.event.Producer
import ro.jf.funds.fund.api.model.CreateTransactionsTO
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.importer.service.domain.*
import ro.jf.funds.importer.service.persistence.ImportTaskRepository
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService
import ro.jf.funds.importer.service.service.parser.ImportParser
import ro.jf.funds.importer.service.service.parser.ImportParserRegistry
import java.util.UUID.randomUUID

class ImportServiceTest {
    private val importParserRegistry = mock<ImportParserRegistry>()
    private val importParser = mock<ImportParser>()
    private val importFundConversionService = mock<ImportFundConversionService>()
    private val importTaskRepository = mock<ImportTaskRepository>()
    private val createFundTransactionsProducer = mock<Producer<CreateTransactionsTO>>()

    private val importService =
        ImportService(
            importTaskRepository,
            importParserRegistry,
            importFundConversionService,
            createFundTransactionsProducer
        )

    private val userId = randomUUID()
    private val importTaskId = randomUUID()

    @Test
    fun `should produce fund transactions request`(): Unit = runBlocking {
        val importType = ImportFileTypeTO.WALLET_CSV
        val configuration = ImportConfigurationTO(importType, emptyList(), emptyList())
        val importFiles = listOf(
            RawImportFile("fileName1", "fileContent1"),
            RawImportFile("fileName2", "fileContent2")
        )
        whenever(importParserRegistry[importType]).thenReturn(importParser)
        val importItems1 = mock<List<ImportParsedTransaction>>()
        val importItems2 = mock<List<ImportParsedTransaction>>()
        whenever(importParser.parse(configuration, listOf(importFiles[0].content))).thenReturn(importItems1)
        whenever(importParser.parse(configuration, listOf(importFiles[1].content))).thenReturn(importItems2)
        val createdImportTask = ImportTask(
            importTaskId,
            userId,
            importFiles.map { ImportTaskPart(randomUUID(), it.name, ImportTaskPartStatus.IN_PROGRESS) })
        whenever(importTaskRepository.startImportTask(StartImportTaskCommand(userId, importFiles.map { it.name })))
            .thenReturn(createdImportTask)
        val fundTransactions1 = mock<CreateTransactionsTO>()
        val fundTransactions2 = mock<CreateTransactionsTO>()
        whenever(importFundConversionService.mapToFundRequest(userId, importItems1))
            .thenReturn(fundTransactions1)
        whenever(importFundConversionService.mapToFundRequest(userId, importItems2))
            .thenReturn(fundTransactions2)

        val importTask = importService.startImport(userId, configuration, importFiles)

        assertThat(importTask.taskId).isEqualTo(importTaskId)
        assertThat(importTask.parts).hasSize(2)
        assertThat(importTask.parts.map { it.status }).containsOnly(ImportTaskPartStatus.IN_PROGRESS)

        val eventCaptor = argumentCaptor<Event<CreateTransactionsTO>>()
        verify(createFundTransactionsProducer, times(2)).send(eventCaptor.capture())
        assertThat(eventCaptor.allValues.map { it.userId }).containsOnly(userId)
        assertThat(eventCaptor.allValues.map { it.correlationId }).containsExactlyInAnyOrderElementsOf(createdImportTask.parts.map { it.taskPartId })
        assertThat(eventCaptor.allValues.map { it.payload })
            .containsExactlyInAnyOrder(fundTransactions1, fundTransactions2)
        assertThat(eventCaptor.allValues.map { it.key }).containsOnly(userId.toString())
    }

    @Test
    fun `should retrieve failed task on parse exception`(): Unit = runBlocking {
        val importType = ImportFileTypeTO.WALLET_CSV
        val configuration = ImportConfigurationTO(importType, emptyList(), emptyList())
        val importFiles = listOf(
            RawImportFile("fileName1", "fileContent1")
        )
        whenever(importParserRegistry[importType]).thenReturn(importParser)
        val reason = "parse error"
        whenever(importParser.parse(configuration, importFiles.map { it.content }))
            .thenThrow(RuntimeException(reason))
        val createdImportTask = ImportTask(
            importTaskId,
            userId,
            importFiles.map { ImportTaskPart(randomUUID(), it.name, ImportTaskPartStatus.IN_PROGRESS) }
        )
        whenever(importTaskRepository.startImportTask(StartImportTaskCommand(userId, importFiles.map { it.name })))
            .thenReturn(createdImportTask)
        whenever(importTaskRepository.findImportTaskById(userId, createdImportTask.taskId))
            .thenReturn(createdImportTask.copy(parts = createdImportTask.parts.map {
                it.copy(
                    status = ImportTaskPartStatus.FAILED,
                    reason = reason
                )
            }))

        val importTask = importService.startImport(userId, configuration, importFiles)

        assertThat(importTask.taskId).isEqualTo(importTaskId)
        assertThat(importTask.parts).hasSize(1)
        assertThat(importTask.parts.first().status).isEqualTo(ImportTaskPartStatus.FAILED)
        assertThat(importTask.parts.first().reason).isEqualTo(reason)

        verify(createFundTransactionsProducer, never()).send(any())
        verify(importTaskRepository).startImportTask(StartImportTaskCommand(userId, importFiles.map { it.name }))
        verify(importTaskRepository).updateTaskPart(
            UpdateImportTaskPartCommand.failed(
                userId,
                createdImportTask.parts.first().taskPartId,
                reason
            )
        )
    }
}
