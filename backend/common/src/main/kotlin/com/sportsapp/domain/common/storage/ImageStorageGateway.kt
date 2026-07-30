package com.sportsapp.domain.common.storage

interface ImageStorageGateway {

    fun createPresignedUpload(
        key: String,
        contentType: String,
        maxBytes: Long,
        expirySeconds: Long = 900,
    ): PresignedUpload

    fun createPresignedDownload(
        key: String,
        expirySeconds: Long = 3600,
    ): String

    fun publicUrl(key: String): String
}
