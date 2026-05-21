package com.sportsapp.domain.common.storage

import java.time.ZonedDateTime

data class PresignedUpload(
    val url: String,
    val key: String,
    val expiresAt: ZonedDateTime,
    val requiredHeaders: Map<String, String>,
)
