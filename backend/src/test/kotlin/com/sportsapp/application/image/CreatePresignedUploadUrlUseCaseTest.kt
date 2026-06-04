package com.sportsapp.application.image

import com.sportsapp.application.image.dto.CreatePresignedUploadUrlCommand
import com.sportsapp.application.image.usecase.CreatePresignedUploadUrlUseCase
import com.sportsapp.domain.common.exceptions.UnsupportedContentTypeException
import com.sportsapp.domain.common.storage.ImageDomainService
import com.sportsapp.domain.common.storage.ImageKeyGenerator
import com.sportsapp.domain.common.storage.PresignedUpload
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class CreatePresignedUploadUrlUseCaseTest : BehaviorSpec({

    val imageDomainService = mockk<ImageDomainService>()
    val useCase = CreatePresignedUploadUrlUseCase(imageDomainService)

    Given("유효한 Command") {
        val command = CreatePresignedUploadUrlCommand(
            filename = "profile.jpg",
            contentType = "image/jpeg",
            domain = "user",
        )
        val expectedPresignedUpload = PresignedUpload(
            url = "https://minio.example.com/sports-app/images/user/uuid.jpg?X-Amz-Signature=abc",
            key = "images/user/uuid.jpg",
            expiresAt = ZonedDateTime.now().plusMinutes(15),
            requiredHeaders = mapOf("Content-Type" to "image/jpeg"),
        )

        every {
            imageDomainService.createUploadUrl(any(), any(), any())
        } returns expectedPresignedUpload

        When("execute를 호출하면") {
            val response = useCase.execute(command)

            Then("[U-01] ImageDomainService.createUploadUrl이 호출되고 URL이 반환된다") {
                verify(exactly = 1) {
                    imageDomainService.createUploadUrl(
                        filename = "profile.jpg",
                        contentType = "image/jpeg",
                        domain = "user",
                    )
                }
                response.url shouldBe expectedPresignedUpload.url
                response.key shouldBe expectedPresignedUpload.key
            }
        }
    }

    Given("filename이 빈 문자열인 Command") {
        When("Command를 생성하면") {
            Then("[U-02] IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    CreatePresignedUploadUrlCommand(
                        filename = "",
                        contentType = "image/jpeg",
                        domain = "user",
                    )
                }
            }
        }
    }

    Given("contentType이 허용 목록 외인 경우") {
        val command = CreatePresignedUploadUrlCommand(
            filename = "doc.pdf",
            contentType = "text/plain",
            domain = "user",
        )

        every {
            imageDomainService.createUploadUrl("doc.pdf", "text/plain", "user")
        } throws UnsupportedContentTypeException("text/plain")

        When("execute를 호출하면") {
            Then("[U-03] UnsupportedContentTypeException이 발생한다") {
                shouldThrow<UnsupportedContentTypeException> {
                    useCase.execute(command)
                }
            }
        }
    }

    Given("domain이 'user'이고 filename이 'avatar.png'인 경우") {
        val imageKeyGenerator = ImageKeyGenerator()

        When("ImageKeyGenerator.generate를 호출하면") {
            val key = imageKeyGenerator.generate("user", "avatar.png")

            Then("[U-04] images/user/<uuid>.png 형식으로 key가 생성된다") {
                key shouldStartWith "images/user/"
                key.endsWith(".png") shouldBe true
            }
        }
    }
})
