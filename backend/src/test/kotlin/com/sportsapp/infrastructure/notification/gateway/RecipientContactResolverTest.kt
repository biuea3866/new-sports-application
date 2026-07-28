package com.sportsapp.infrastructure.notification.gateway

import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.gateway.RecipientContactGateway
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.vo.NotificationPayload
import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * RecipientContactResolver — user 컨텍스트 저장소 직접 의존을 제거하고
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

            Then("payload의 phone 값을 그대로 반환한다") {
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

            Then("null을 반환한다") {
                phone shouldBe null
            }
        }
    }

    // 전환 회귀 가드. 음성 단언만 두면 vacuous 하게 통과한다 — 생성자를 못 읽어 빈 리스트가 되거나
    // (필드 주입 회귀), 검사하지 않은 우회 타입(UserJpaRepository·UserCustomRepositoryImpl·
    // UserDomainService 직접 주입)으로 결합이 되살아나도 잡히지 않는다. 그래서 ① 파라미터를 실제로
    // 읽었다는 양성 단언 ② 기대 의존을 포함한다는 양성 단언 ③ 타입 단건이 아니라 user 컨텍스트
    // 패키지 전체를 금지하는 단언을 함께 건다. 신설 어댑터(GatewayImpl)도 같은 가드로 덮는다.
    Given("notification infrastructure 어댑터들의 생성자 시그니처를 검사하면") {
        // Resolver 는 user 컨텍스트의 어떤 타입도 알면 안 되므로 상위 prefix 2종으로 넓게 덮는다 —
        // 하위 패키지를 열거하면 domain.user.gateway 나 앞으로 생길 서브패키지가 빠진다.
        val userContextPackages = listOf(
            "com.sportsapp.domain.user",
            "com.sportsapp.infrastructure.user",
        )

        When("RecipientContactResolver의 생성자 파라미터 타입을 조회하면") {
            val parameters = requireNotNull(RecipientContactResolver::class.primaryConstructor) {
                "RecipientContactResolver must expose a primary constructor"
            }.parameters
            val parameterTypes = parameters.mapNotNull { it.type.classifier as? KClass<*> }

            Then("파라미터를 실제로 읽었고 ACL 게이트웨이만 의존한다") {
                parameterTypes.shouldNotBeEmpty()
                parameterTypes.shouldContain(RecipientContactGateway::class)
            }

            Then("user 컨텍스트 패키지의 어떤 타입에도 의존하지 않는다") {
                parameterTypes
                    .filter { type -> userContextPackages.any { type.qualifiedName.orEmpty().startsWith(it) } }
                    .shouldBeEmpty()
            }
        }

        When("RecipientContactGatewayImpl의 생성자 파라미터 타입을 조회하면") {
            val parameters = requireNotNull(RecipientContactGatewayImpl::class.primaryConstructor) {
                "RecipientContactGatewayImpl must expose a primary constructor"
            }.parameters
            val parameterTypes = parameters.mapNotNull { it.type.classifier as? KClass<*> }

            Then("공급자 저장소가 아니라 공급자 DomainService만 의존한다") {
                parameterTypes.shouldNotBeEmpty()
                // 양성 단언 — 이름이 "DomainService만 의존한다"이므로 금지 단언만으로는 범위가 어긋난다.
                parameterTypes.shouldContain(UserDomainService::class)
                parameterTypes
                    .filter { type -> type.qualifiedName.orEmpty().startsWith("com.sportsapp.domain.user.repository") }
                    .shouldBeEmpty()
                parameterTypes
                    .filter { type -> type.qualifiedName.orEmpty().startsWith("com.sportsapp.infrastructure.user") }
                    .shouldBeEmpty()
            }
        }
    }
})
