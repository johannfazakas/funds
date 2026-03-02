package ro.jf.funds.importer.service.service.event

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectResponse
import aws.smithy.kotlin.runtime.content.ByteStream
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.*
import ro.jf.funds.fund.api.model.CreateTransactionsTO
import ro.jf.funds.importer.api.model.ImportFileCommandTO
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.importer.service.config.S3Configuration
import ro.jf.funds.importer.service.domain.*
import ro.jf.funds.importer.service.persistence.ImportFileRepository
import ro.jf.funds.importer.service.service.ImportConfigurationService
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService
import ro.jf.funds.importer.service.service.parser.ImportParser
import ro.jf.funds.importer.service.service.parser.ImportParserRegistry
import ro.jf.funds.platform.jvm.event.Event
import ro.jf.funds.platform.jvm.event.Producer
import java.time.LocalDateTime
import java.util.UUID.randomUUID
import kotlin.time.Duration.Companion.minutes

class ImportFileCommandHandlerTest {
    private val importFileRepository = mock<ImportFileRepository>()
    private val importConfigurationService = mock<ImportConfigurationService>()
    private val importParserRegistry = mock<ImportParserRegistry>()
    private val importFundConversionService = mock<ImportFundConversionService>()
    private val createFundTransactionsProducer = mock<Producer<CreateTransactionsTO>>()
    private val s3Client = mock<S3Client>()
    private val s3Configuration = S3Configuration(
        bucket = "test-bucket",
        endpoint = "http://localhost:4566",
        publicEndpoint = "http://localhost:4566",
        presignedUrlExpiration = 15.minutes,
    )

    private val handler = ImportFileCommandHandler(
        importFileRepository,
        importConfigurationService,
        importParserRegistry,
        importFundConversionService,
        createFundTransactionsProducer,
        s3Client,
        s3Configuration,
    )

    private val userId = randomUUID()
    private val importFileId = randomUUID()
    private val importConfigurationId = randomUUID()

    @Test
    fun `given import file not found should skip processing`(): Unit = runBlocking {
        whenever(importFileRepository.findById(userId, importFileId)).thenReturn(null)

        handler.handle(createEvent())

        verifyNoInteractions(s3Client)
        verifyNoInteractions(createFundTransactionsProducer)
    }

    @Test
    fun `given import file not in importing status should skip processing`(): Unit = runBlocking {
        whenever(importFileRepository.findById(userId, importFileId))
            .thenReturn(createImportFile(ImportFileStatus.UPLOADED))

        handler.handle(createEvent())

        verifyNoInteractions(s3Client)
        verifyNoInteractions(createFundTransactionsProducer)
    }

    @Test
    fun `given import configuration not found should mark file as failed`(): Unit = runBlocking {
        whenever(importFileRepository.findById(userId, importFileId))
            .thenReturn(createImportFile(ImportFileStatus.IMPORTING))
        stubS3Download("csv-content")
        whenever(importConfigurationService.getImportConfiguration(userId, importConfigurationId))
            .thenReturn(null)

        handler.handle(createEvent())

        verify(importFileRepository).updateStatusWithErrors(
            eq(userId), eq(importFileId), eq(ImportFileStatus.IMPORT_FAILED), any()
        )
        verifyNoInteractions(createFundTransactionsProducer)
    }

    @Test
    fun `given successful import should parse and send transactions`(): Unit = runBlocking {
        val importFile = createImportFile(ImportFileStatus.IMPORTING)
        whenever(importFileRepository.findById(userId, importFileId)).thenReturn(importFile)
        stubS3Download("csv-content")
        val matchers = ImportMatchers()
        val configuration = ImportConfiguration(importConfigurationId, userId, "test", matchers, LocalDateTime.now())
        whenever(importConfigurationService.getImportConfiguration(userId, importConfigurationId))
            .thenReturn(configuration)
        val parser = mock<ImportParser>()
        val parsedTransactions = listOf<ImportParsedTransaction>()
        whenever(importParserRegistry[ImportFileTypeTO.WALLET_CSV]).thenReturn(parser)
        whenever(parser.parse(matchers, "csv-content")).thenReturn(parsedTransactions)
        val fundTransactions = mock<CreateTransactionsTO>()
        whenever(importFundConversionService.mapToFundRequest(userId, parsedTransactions))
            .thenReturn(fundTransactions)

        handler.handle(createEvent())

        verify(createFundTransactionsProducer).send(Event(userId, fundTransactions, importFileId))
        verify(importFileRepository, never()).updateStatusWithErrors(any(), any(), any(), any())
    }

    @Test
    fun `given parsing error should mark file as failed`(): Unit = runBlocking {
        val importFile = createImportFile(ImportFileStatus.IMPORTING)
        whenever(importFileRepository.findById(userId, importFileId)).thenReturn(importFile)
        stubS3Download("bad-content")
        val matchers = ImportMatchers()
        val configuration = ImportConfiguration(importConfigurationId, userId, "test", matchers, LocalDateTime.now())
        whenever(importConfigurationService.getImportConfiguration(userId, importConfigurationId))
            .thenReturn(configuration)
        val parser = mock<ImportParser>()
        whenever(importParserRegistry[ImportFileTypeTO.WALLET_CSV]).thenReturn(parser)
        whenever(parser.parse(matchers, "bad-content")).thenThrow(RuntimeException("Parse error"))

        handler.handle(createEvent())

        verify(importFileRepository).updateStatusWithErrors(
            eq(userId), eq(importFileId), eq(ImportFileStatus.IMPORT_FAILED), any()
        )
        verifyNoInteractions(createFundTransactionsProducer)
    }

    private fun createEvent(): Event<ImportFileCommandTO> {
        val command = ImportFileCommandTO(
            importFileId = com.benasher44.uuid.Uuid.fromString(importFileId.toString())
        )
        return Event(userId, command)
    }

    private fun createImportFile(status: ImportFileStatus) = ImportFile(
        importFileId = importFileId,
        userId = userId,
        fileName = "test.csv",
        type = ImportFileTypeTO.WALLET_CSV,
        status = status,
        importConfigurationId = importConfigurationId,
        createdAt = LocalDateTime.now(),
    )

    @Suppress("UNCHECKED_CAST")
    private fun stubS3Download(content: String) = runBlocking {
        whenever(s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> String>()))
            .thenAnswer { invocation ->
                val block = invocation.arguments[1] as suspend (GetObjectResponse) -> String
                runBlocking {
                    val response = GetObjectResponse.invoke {
                        this.body = ByteStream.fromString(content)
                    }
                    block(response)
                }
            }
    }
}
