package ro.jf.funds.importer.service.service

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.HeadObjectRequest
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.sdk.kotlin.services.s3.presigners.presignPutObject
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.importer.service.domain.CreateImportFileCommand
import ro.jf.funds.importer.service.domain.CreateImportFileResponse
import ro.jf.funds.importer.service.domain.ImportFile
import ro.jf.funds.importer.service.persistence.ImportFileRepository
import java.util.*
import kotlin.time.Duration

class ImportFileService(
    private val importFileRepository: ImportFileRepository,
    private val s3Client: S3Client,
    private val bucket: String,
    private val s3Endpoint: String,
    private val s3PublicEndpoint: String,
    private val presignedUrlExpiration: Duration,
) {
    suspend fun createImportFile(userId: UUID, fileName: String, type: ImportFileTypeTO): CreateImportFileResponse {
        val s3Key = "$userId/$fileName"
        val importFile = importFileRepository.create(CreateImportFileCommand(userId, fileName, type, s3Key))
        val uploadUrl = generateUploadUrl(importFile.s3Key)
        return CreateImportFileResponse(importFile, uploadUrl)
    }

    suspend fun confirmUpload(userId: UUID, importFileId: UUID): ImportFile? {
        val existing = importFileRepository.findById(userId, importFileId) ?: return null
        if (!exists(existing.s3Key)) return null
        return importFileRepository.confirmUpload(userId, importFileId)
    }

    suspend fun getImportFile(userId: UUID, importFileId: UUID): ImportFile? {
        return importFileRepository.findById(userId, importFileId)
    }

    suspend fun listImportFiles(userId: UUID): List<ImportFile> {
        return importFileRepository.listByUserId(userId)
    }

    suspend fun deleteImportFile(userId: UUID, importFileId: UUID): Boolean {
        val importFile = importFileRepository.findById(userId, importFileId) ?: return false
        deleteS3Object(importFile.s3Key)
        return importFileRepository.delete(userId, importFileId)
    }

    suspend fun generateDownloadUrl(userId: UUID, importFileId: UUID): String? {
        val importFile = importFileRepository.findById(userId, importFileId) ?: return null
        return generateDownloadUrl(importFile.s3Key)
    }

    private suspend fun generateUploadUrl(s3Key: String): String {
        val request = PutObjectRequest {
            this.bucket = this@ImportFileService.bucket
            this.key = s3Key
        }
        val presigned = s3Client.presignPutObject(request, presignedUrlExpiration)
        return presigned.url.toString().toPublicUrl()
    }

    private suspend fun generateDownloadUrl(s3Key: String): String {
        val request = GetObjectRequest {
            this.bucket = this@ImportFileService.bucket
            this.key = s3Key
        }
        val presigned = s3Client.presignGetObject(request, presignedUrlExpiration)
        return presigned.url.toString().toPublicUrl()
    }

    private suspend fun deleteS3Object(s3Key: String) {
        s3Client.deleteObject(DeleteObjectRequest {
            this.bucket = this@ImportFileService.bucket
            this.key = s3Key
        })
    }

    private fun String.toPublicUrl(): String =
        replaceFirst(s3Endpoint, s3PublicEndpoint)

    private suspend fun exists(s3Key: String): Boolean {
        return try {
            s3Client.headObject(HeadObjectRequest {
                this.bucket = this@ImportFileService.bucket
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
