package com.sportsapp.application.image

import com.sportsapp.domain.common.storage.ImageDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatePresignedUploadUrlUseCase(
    private val imageDomainService: ImageDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: CreatePresignedUploadUrlCommand): CreatePresignedUploadUrlResponse {
        val presignedUpload = imageDomainService.createUploadUrl(
            filename = command.filename,
            contentType = command.contentType,
            domain = command.domain,
        )
        return CreatePresignedUploadUrlResponse.of(presignedUpload)
    }
}
