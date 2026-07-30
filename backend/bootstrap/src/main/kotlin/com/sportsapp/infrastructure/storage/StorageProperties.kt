package com.sportsapp.infrastructure.storage

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "storage.image")
data class StorageProperties(
    val endpoint: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val uploadExpirySeconds: Long,
    val downloadExpirySeconds: Long,
    val maxBytes: Long,
    val allowedContentTypes: List<String>,
)
