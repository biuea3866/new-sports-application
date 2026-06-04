package com.sportsapp.presentation.image.dto.request

import com.sportsapp.application.image.dto.CreatePresignedUploadUrlCommand

data class PresignedUploadRequest(
    val filename: String,
    val contentType: String,
    val domain: String,
) {
    fun toCommand(): CreatePresignedUploadUrlCommand =
        CreatePresignedUploadUrlCommand(
            filename = filename,
            contentType = contentType,
            domain = domain,
        )
}
