package com.sportsapp.application.image

import com.sportsapp.domain.common.exceptions.UnsupportedContentTypeException

private val ALLOWED_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")

data class CreatePresignedUploadUrlCommand(
    val filename: String,
    val contentType: String,
    val domain: String,
) {
    init {
        require(filename.isNotBlank()) { "filename must not be blank" }
        require(domain.isNotBlank()) { "domain must not be blank" }
        if (contentType !in ALLOWED_CONTENT_TYPES) throw UnsupportedContentTypeException(contentType)
    }
}
