package com.sportsapp.domain.common.storage

import com.sportsapp.domain.common.exceptions.UnsupportedContentTypeException

class ImageDomainService(
    private val imageStorageGateway: ImageStorageGateway,
    private val imageKeyGenerator: ImageKeyGenerator,
    private val allowedContentTypes: List<String>,
    private val maxBytes: Long,
    private val uploadExpirySeconds: Long,
) {
    fun createUploadUrl(filename: String, contentType: String, domain: String): PresignedUpload {
        if (contentType !in allowedContentTypes) throw UnsupportedContentTypeException(contentType)
        val key = imageKeyGenerator.generate(domain, filename)
        return imageStorageGateway.createPresignedUpload(
            key = key,
            contentType = contentType,
            maxBytes = maxBytes,
            expirySeconds = uploadExpirySeconds,
        )
    }
}
