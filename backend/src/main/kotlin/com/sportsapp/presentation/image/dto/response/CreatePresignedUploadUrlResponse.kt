package com.sportsapp.presentation.image.dto.response

import com.sportsapp.domain.common.storage.PresignedUpload
import java.time.ZonedDateTime

data class CreatePresignedUploadUrlResponse(
    val url: String,
    val key: String,
    val expiresAt: ZonedDateTime,
    val requiredHeaders: Map<String, String>,
) {
    companion object {
        fun of(presignedUpload: PresignedUpload): CreatePresignedUploadUrlResponse =
            CreatePresignedUploadUrlResponse(
                url = presignedUpload.url,
                key = presignedUpload.key,
                expiresAt = presignedUpload.expiresAt,
                requiredHeaders = presignedUpload.requiredHeaders,
            )
    }
}
