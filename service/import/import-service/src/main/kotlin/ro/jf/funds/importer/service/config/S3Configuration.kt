package ro.jf.funds.importer.service.config

import kotlin.time.Duration

data class S3Configuration(
    val bucket: String,
    val endpoint: String,
    val publicEndpoint: String,
    val presignedUrlExpiration: Duration,
)
