package com.sportsapp.domain.common.storage

import com.sportsapp.domain.common.exceptions.UnsupportedContentTypeException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.ZonedDateTime

class ImageDomainServiceTest : BehaviorSpec({

    val imageStorageGateway = mockk<ImageStorageGateway>()
    val imageKeyGenerator = ImageKeyGenerator()
    val allowedContentTypes = listOf("image/jpeg", "image/png", "image/webp")
    val maxBytes = 10_485_760L
    val uploadExpirySeconds = 900L

    val imageDomainService = ImageDomainService(
        imageStorageGateway = imageStorageGateway,
        imageKeyGenerator = imageKeyGenerator,
        allowedContentTypes = allowedContentTypes,
        maxBytes = maxBytes,
        uploadExpirySeconds = uploadExpirySeconds,
    )

    Given("허용된 contentType image/jpeg") {
        val keySlot = slot<String>()
        every {
            imageStorageGateway.createPresignedUpload(
                key = capture(keySlot),
                contentType = "image/jpeg",
                maxBytes = maxBytes,
                expirySeconds = uploadExpirySeconds,
            )
        } returns PresignedUpload(
            url = "https://minio/presigned",
            key = "images/user/uuid.jpg",
            expiresAt = ZonedDateTime.now().plusSeconds(uploadExpirySeconds),
            requiredHeaders = mapOf("Content-Type" to "image/jpeg"),
        )

        When("createUploadUrl을 호출하면") {
            val result = imageDomainService.createUploadUrl(
                filename = "photo.jpg",
                contentType = "image/jpeg",
                domain = "user",
            )

            Then("[U-01] PresignedUpload URL이 반환되고 key가 images/user/ 형식이다") {
                result.url shouldBe "https://minio/presigned"
                keySlot.captured.startsWith("images/user/") shouldBe true
                keySlot.captured.endsWith(".jpg") shouldBe true
            }
        }
    }

    Given("허용되지 않은 contentType text/plain") {
        When("createUploadUrl을 호출하면") {
            Then("[U-02] UnsupportedContentTypeException이 발생한다") {
                shouldThrow<UnsupportedContentTypeException> {
                    imageDomainService.createUploadUrl(
                        filename = "doc.txt",
                        contentType = "text/plain",
                        domain = "user",
                    )
                }
            }
        }
    }

    Given("확장자 없는 filename") {
        val keySlot = slot<String>()
        every {
            imageStorageGateway.createPresignedUpload(
                key = capture(keySlot),
                contentType = "image/png",
                maxBytes = maxBytes,
                expirySeconds = uploadExpirySeconds,
            )
        } returns PresignedUpload(
            url = "https://minio/presigned",
            key = "images/user/uuid",
            expiresAt = ZonedDateTime.now().plusSeconds(uploadExpirySeconds),
            requiredHeaders = emptyMap(),
        )

        When("createUploadUrl을 호출하면") {
            val result = imageDomainService.createUploadUrl(
                filename = "photo",
                contentType = "image/png",
                domain = "user",
            )

            Then("[U-03] 확장자 없이 images/user/<uuid> 형식으로 key가 생성된다") {
                result.url shouldBe "https://minio/presigned"
                keySlot.captured.startsWith("images/user/") shouldBe true
                keySlot.captured.contains(".") shouldBe false
            }
        }
    }
})
