package com.sportsapp.presentation.image

import com.sportsapp.application.image.CreatePresignedUploadUrlCommand

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
