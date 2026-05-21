package com.sportsapp.infrastructure.storage

import com.sportsapp.domain.common.storage.ImageDomainService
import com.sportsapp.domain.common.storage.ImageKeyGenerator
import com.sportsapp.domain.common.storage.ImageStorageGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ImageDomainServiceConfig {

    @Bean
    fun imageDomainService(
        imageStorageGateway: ImageStorageGateway,
        imageKeyGenerator: ImageKeyGenerator,
        storageProperties: StorageProperties,
    ): ImageDomainService = ImageDomainService(
        imageStorageGateway = imageStorageGateway,
        imageKeyGenerator = imageKeyGenerator,
        allowedContentTypes = storageProperties.allowedContentTypes,
        maxBytes = storageProperties.maxBytes,
        uploadExpirySeconds = storageProperties.uploadExpirySeconds,
    )
}
