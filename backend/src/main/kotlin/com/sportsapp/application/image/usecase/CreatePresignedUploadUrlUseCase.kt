package com.sportsapp.application.image.usecase

import com.sportsapp.application.image.dto.CreatePresignedUploadUrlCommand
import com.sportsapp.domain.common.storage.ImageDomainService
import com.sportsapp.domain.common.storage.PresignedUpload
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatePresignedUploadUrlUseCase(
    private val imageDomainService: ImageDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: CreatePresignedUploadUrlCommand): PresignedUpload =
        imageDomainService.createUploadUrl(
            filename = command.filename,
            contentType = command.contentType,
            domain = command.domain,
        )
}
