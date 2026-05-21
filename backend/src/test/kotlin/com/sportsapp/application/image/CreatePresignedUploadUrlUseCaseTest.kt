package com.sportsapp.application.image

import com.sportsapp.domain.common.exceptions.UnsupportedContentTypeException
import com.sportsapp.domain.common.storage.ImageStorageGateway
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

    val imageStorageGateway = mockk<ImageStorageGateway>()
    val imageKeyGenerator = ImageKeyGenerator()
    val useCase = CreatePresignedUploadUrlUseCase(imageStorageGateway, imageKeyGenerator)

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
            imageStorageGateway.createPresignedUpload(any(), any(), any(), any())
        } returns expectedPresignedUpload

        When("execute를 호출하면") {
            val response = useCase.execute(command)

            Then("[U-01] ImageStorageGateway.createPresignedUpload가 호출되고 URL이 반환된다") {
                verify(exactly = 1) {
                    imageStorageGateway.createPresignedUpload(
                        key = any(),
                        contentType = "image/jpeg",
                        maxBytes = 10_485_760L,
                        expirySeconds = any(),
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

    Given("contentType이 허용 목록 외인 Command") {
        When("Command를 생성하면") {
            Then("[U-03] UnsupportedContentTypeException이 발생한다") {
                shouldThrow<UnsupportedContentTypeException> {
                    CreatePresignedUploadUrlCommand(
                        filename = "doc.pdf",
                        contentType = "text/plain",
                        domain = "user",
                    )
                }
            }
        }
    }

    Given("domain이 'user'이고 filename이 'avatar.png'인 Command") {
        When("ImageKeyGenerator.generate를 호출하면") {
            val key = imageKeyGenerator.generate("user", "avatar.png")

            Then("[U-04] images/user/<uuid>.png 형식으로 key가 생성된다") {
                key shouldStartWith "images/user/"
                key.endsWith(".png") shouldBe true
            }
        }
    }
})
