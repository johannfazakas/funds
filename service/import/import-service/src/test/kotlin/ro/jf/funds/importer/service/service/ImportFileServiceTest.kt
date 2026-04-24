package ro.jf.funds.importer.service.service

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectResponse
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import ro.jf.funds.fund.sdk.TransactionSdk
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.importer.service.config.S3Configuration
import ro.jf.funds.importer.service.domain.ImportFile
import ro.jf.funds.importer.service.domain.ImportFileStatus
import ro.jf.funds.importer.service.persistence.ImportFileRepository
import ro.jf.funds.platform.jvm.event.Producer
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes

class ImportFileServiceTest {
    private val importFileRepository = mock<ImportFileRepository>()
    private val importFileCommandProducer = mock<Producer<Any>>()
    private val transactionSdk = mock<TransactionSdk>()
    private val s3Client = mock<S3Client>()
    private val s3Configuration = S3Configuration(
        endpoint = "http://localhost:4566",
        publicEndpoint = "http://localhost:4566",
        bucket = "imports",
        presignedUrlExpiration = 15.minutes,
    )

    private val importFileService = ImportFileService(
        importFileRepository = importFileRepository,
        importFileCommandProducer = importFileCommandProducer as Producer<ro.jf.funds.importer.api.model.ImportFileCommandTO>,
        transactionSdk = transactionSdk,
        s3Client = s3Client,
        s3Configuration = s3Configuration,
    )

    @Test
    fun `given imported file - when deleting - then should revert transactions before deleting`(): Unit = runBlocking {
        val userId = uuid4()
        val importFileId = uuid4()
        val configurationId = uuid4()
        val importFile = ImportFile(
            importFileId = importFileId,
            userId = userId,
            fileName = "test.csv",
            type = ImportFileTypeTO.WALLET_CSV,
            status = ImportFileStatus.IMPORTED,
            importConfigurationId = configurationId,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        whenever(importFileRepository.findById(eq(userId), eq(importFileId))).thenReturn(importFile)
        whenever(s3Client.deleteObject(any<DeleteObjectRequest>())).thenReturn(DeleteObjectResponse {})
        whenever(importFileRepository.delete(eq(userId), eq(importFileId))).thenReturn(true)

        val result = importFileService.deleteImportFile(userId, importFileId)

        assertThat(result).isTrue()
        verify(transactionSdk).deleteTransactionsBySource(eq(userId), eq("import-file-$importFileId"))
        verify(importFileRepository).delete(eq(userId), eq(importFileId))
    }

    @Test
    fun `given uploaded file - when deleting - then should not revert transactions`(): Unit = runBlocking {
        val userId = uuid4()
        val importFileId = uuid4()
        val configurationId = uuid4()
        val importFile = ImportFile(
            importFileId = importFileId,
            userId = userId,
            fileName = "test.csv",
            type = ImportFileTypeTO.WALLET_CSV,
            status = ImportFileStatus.UPLOADED,
            importConfigurationId = configurationId,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        whenever(importFileRepository.findById(eq(userId), eq(importFileId))).thenReturn(importFile)
        whenever(s3Client.deleteObject(any<DeleteObjectRequest>())).thenReturn(DeleteObjectResponse {})
        whenever(importFileRepository.delete(eq(userId), eq(importFileId))).thenReturn(true)

        val result = importFileService.deleteImportFile(userId, importFileId)

        assertThat(result).isTrue()
        verifyNoInteractions(transactionSdk)
        verify(importFileRepository).delete(eq(userId), eq(importFileId))
    }
}
