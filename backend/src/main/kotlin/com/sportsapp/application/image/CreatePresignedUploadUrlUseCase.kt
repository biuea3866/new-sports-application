package com.sportsapp.application.image

import com.sportsapp.domain.common.storage.ImageStorageGateway
import org.springframework.stereotype.Service

@Service
class CreatePresignedUploadUrlUseCase(
    private val imageStorageGateway: ImageStorageGateway,
    private val imageKeyGenerator: ImageKeyGenerator,
) {
    fun execute(command: CreatePresignedUploadUrlCommand): CreatePresignedUploadUrlResponse {
        val key = imageKeyGenerator.generate(command.domain, command.filename)
        val presignedUpload = imageStorageGateway.createPresignedUpload(
            key = key,
            contentType = command.contentType,
            maxBytes = MAX_BYTES,
        )
        return CreatePresignedUploadUrlResponse.of(presignedUpload)
    }

    companion object {
        private const val MAX_BYTES = 10_485_760L
    }
}
