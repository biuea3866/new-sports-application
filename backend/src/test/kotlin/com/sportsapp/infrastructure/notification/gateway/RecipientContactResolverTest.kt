package com.sportsapp.infrastructure.notification.gateway

import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.gateway.RecipientContactGateway
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.vo.NotificationPayload
import com.sportsapp.domain.user.repository.UserRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.reflect.full.primaryConstructor

/**
 * [PH0-04] RecipientContactResolver — UserRepository 직접 의존을 제거하고
 * RecipientContactGateway(ACL)로 위임하는지 검증한다.
 */
class RecipientContactResolverTest : BehaviorSpec({

    val recipientContactGateway = mockk<RecipientContactGateway>()
    val resolver = RecipientContactResolver(recipientContactGateway)

    Given("존재하는 사용자의 이메일을 조회하면") {
        every { recipientContactGateway.emailOf(10L) } returns "test@example.com"

        When("emailOf를 호출하면") {
            val email = resolver.emailOf(10L)

            Then("RecipientContactGateway가 반환한 이메일을 그대로 반환한다") {
                email shouldBe "test@example.com"
                verify(exactly = 1) { recipientContactGateway.emailOf(10L) }
            }
        }
    }

    Given("존재하지 않는 사용자의 이메일을 조회하면") {
        every { recipientContactGateway.emailOf(999L) } returns null

        When("emailOf를 호출하면") {
            val email = resolver.emailOf(999L)

            Then("예외 없이 null을 반환한다") {
                email shouldBe null
            }
        }
    }

    Given("알림 payload에 phone 키가 있으면") {
        val notification = Notification.queue(
            userId = 1L,
            channel = NotificationChannel.SMS,
            templateId = "template-1",
            payload = NotificationPayload(mapOf("phone" to "010-1234-5678")),
        )

        When("phoneOf를 호출하면") {
            val phone = resolver.phoneOf(notification)

            Then("[기존 동작 보존] payload의 phone 값을 그대로 반환한다") {
                phone shouldBe "010-1234-5678"
            }
        }
    }

    Given("알림 payload에 phone 키가 없으면") {
        val notification = Notification.queue(
            userId = 1L,
            channel = NotificationChannel.SMS,
            templateId = "template-1",
            payload = NotificationPayload(emptyMap()),
        )

        When("phoneOf를 호출하면") {
            val phone = resolver.phoneOf(notification)

            Then("[기존 동작 보존] null을 반환한다") {
                phone shouldBe null
            }
        }
    }

    Given("RecipientContactResolver의 생성자 시그니처를 검사하면") {
        When("primary constructor 파라미터 타입을 조회하면") {
            val constructorParameterTypes = RecipientContactResolver::class.primaryConstructor
                ?.parameters
                ?.map { it.type.classifier }
                .orEmpty()

            Then("[전환 자체 검증] UserRepository 타입 의존이 남아 있지 않다") {
                constructorParameterTypes shouldNotContain UserRepository::class
            }
        }
    }
})
