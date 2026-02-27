package ro.jf.funds.importer.service.service

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.HeadObjectRequest
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.sdk.kotlin.services.s3.presigners.presignPutObject
import ro.jf.funds.importer.api.model.ImportFileCommandTO
import ro.jf.funds.importer.api.model.ImportFileSortField
import ro.jf.funds.importer.service.config.S3Configuration
import ro.jf.funds.importer.service.domain.CreateImportFileCommand
import ro.jf.funds.importer.service.domain.CreateImportFileResponse
import ro.jf.funds.importer.service.domain.ImportFile
import ro.jf.funds.importer.service.domain.ImportFileFilter
import ro.jf.funds.importer.service.domain.ImportFileStatus
import ro.jf.funds.importer.service.domain.exception.ImportFileNotFoundException
import ro.jf.funds.importer.service.domain.exception.ImportFileStatusConflictException
import ro.jf.funds.importer.service.persistence.ImportFileRepository
import ro.jf.funds.platform.api.model.PageRequest
import ro.jf.funds.platform.api.model.SortRequest
import ro.jf.funds.platform.jvm.event.Event
import ro.jf.funds.platform.jvm.event.Producer
import ro.jf.funds.platform.jvm.persistence.PagedResult
import java.util.*

class ImportFileService(
    private val importFileRepository: ImportFileRepository,
    private val importFileCommandProducer: Producer<ImportFileCommandTO>,
    private val s3Client: S3Client,
    private val s3Configuration: S3Configuration,
) {
    suspend fun createImportFile(command: CreateImportFileCommand): CreateImportFileResponse {
        val importFile = importFileRepository.create(command)
        val uploadUrl = generateUploadUrl(importFile.s3Key)
        return CreateImportFileResponse(importFile, uploadUrl)
    }

    suspend fun confirmUpload(userId: UUID, importFileId: UUID): ImportFile {
        val existing = importFileRepository.findById(userId, importFileId)
            ?: throw ImportFileNotFoundException(importFileId)
        if (!exists(existing.s3Key))
            throw ImportFileStatusConflictException(importFileId)
        return importFileRepository.confirmUpload(userId, importFileId)
            ?: throw ImportFileNotFoundException(importFileId)
    }

    suspend fun getImportFile(userId: UUID, importFileId: UUID): ImportFile? {
        return importFileRepository.findById(userId, importFileId)
    }

    suspend fun listImportFiles(
        userId: UUID,
        filter: ImportFileFilter? = null,
        pageRequest: PageRequest? = null,
        sortRequest: SortRequest<ImportFileSortField>? = null,
    ): PagedResult<ImportFile> {
        return importFileRepository.list(userId, filter, pageRequest, sortRequest)
    }

    suspend fun deleteImportFile(userId: UUID, importFileId: UUID): Boolean {
        val importFile = importFileRepository.findById(userId, importFileId) ?: return false
        deleteS3Object(importFile.s3Key)
        return importFileRepository.delete(userId, importFileId)
    }

    suspend fun importFile(userId: UUID, importFileId: UUID): ImportFile {
        val importFile = importFileRepository.findById(userId, importFileId)
            ?: throw ImportFileNotFoundException(importFileId)
        if (importFile.status != ImportFileStatus.UPLOADED) {
            throw ImportFileStatusConflictException(importFileId)
        }
        importFileRepository.updateStatus(userId, importFileId, ImportFileStatus.IMPORTING)
        val command = ImportFileCommandTO(
            importFileId = com.benasher44.uuid.Uuid.fromString(importFileId.toString()),
        )
        importFileCommandProducer.send(Event(userId, command))
        return importFile.copy(status = ImportFileStatus.IMPORTING)
    }

    suspend fun generateDownloadUrl(userId: UUID, importFileId: UUID): String? {
        val importFile = importFileRepository.findById(userId, importFileId) ?: return null
        return generateDownloadUrl(importFile.s3Key)
    }

    private suspend fun generateUploadUrl(s3Key: String): String {
        val request = PutObjectRequest {
            this.bucket = s3Configuration.bucket
            this.key = s3Key
        }
        val presigned = s3Client.presignPutObject(request, s3Configuration.presignedUrlExpiration)
        return presigned.url.toString().toPublicUrl()
    }

    private suspend fun generateDownloadUrl(s3Key: String): String {
        val request = GetObjectRequest {
            this.bucket = s3Configuration.bucket
            this.key = s3Key
        }
        val presigned = s3Client.presignGetObject(request, s3Configuration.presignedUrlExpiration)
        return presigned.url.toString().toPublicUrl()
    }

    private suspend fun deleteS3Object(s3Key: String) {
        s3Client.deleteObject(DeleteObjectRequest {
            this.bucket = s3Configuration.bucket
            this.key = s3Key
        })
    }

    private fun String.toPublicUrl(): String =
        replaceFirst(s3Configuration.endpoint, s3Configuration.publicEndpoint)

    private suspend fun exists(s3Key: String): Boolean {
        return try {
            s3Client.headObject(HeadObjectRequest {
                this.bucket = s3Configuration.bucket
                this.key = s3Key
            })
            true
        } catch (_: NoSuchKey) {
            false
        } catch (_: NotFound) {
            false
        }
    }
}
