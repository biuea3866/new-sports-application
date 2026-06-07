package com.sportsapp.domain.notification

import com.sportsapp.domain.notification.entity.PushPlatform
import com.sportsapp.domain.notification.entity.PushToken
import com.sportsapp.domain.notification.repository.PushTokenRepository
import com.sportsapp.domain.notification.service.PushTokenDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class PushTokenDomainServiceTest : BehaviorSpec({

    val pushTokenRepository = mockk<PushTokenRepository>()
    val service = PushTokenDomainService(pushTokenRepository)

    every { pushTokenRepository.save(any()) } answers { firstArg() }

    Given("등록되지 않은 토큰") {
        every { pushTokenRepository.findByToken("ExpoTok[A]") } returns null

        When("[U-01] 등록하면") {
            val result = service.register(userId = 1L, token = "ExpoTok[A]", platform = PushPlatform.ANDROID)

            Then("새 PushToken 이 저장된다") {
                result.userId shouldBe 1L
                result.platform shouldBe PushPlatform.ANDROID
            }
        }
    }

    Given("이미 다른 사용자에게 등록된 토큰") {
        val existing = PushToken.create(userId = 1L, token = "ExpoTok[B]", platform = PushPlatform.IOS)
        every { pushTokenRepository.findByToken("ExpoTok[B]") } returns existing

        When("[U-02] 다른 사용자/플랫폼으로 재등록하면") {
            val result = service.register(userId = 2L, token = "ExpoTok[B]", platform = PushPlatform.ANDROID)

            Then("새로 만들지 않고 소유자/플랫폼을 갱신한다") {
                result.userId shouldBe 2L
                result.platform shouldBe PushPlatform.ANDROID
                verify(exactly = 1) { pushTokenRepository.save(existing) }
            }
        }
    }

    Given("빈 토큰") {
        When("[U-03] 등록하면") {
            Then("IllegalArgumentException 이 발생한다") {
                every { pushTokenRepository.findByToken("") } returns null
                try {
                    service.register(userId = 1L, token = "", platform = PushPlatform.WEB)
                    throw AssertionError("예외가 발생해야 한다")
                } catch (e: IllegalArgumentException) {
                    (e.message ?: "") shouldBe "push token must not be blank"
                }
            }
        }
    }
})
