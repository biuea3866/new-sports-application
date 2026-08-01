package com.sportsapp.infrastructure.notification

import com.sportsapp.domain.notification.exception.UnknownTemplateException
import com.sportsapp.infrastructure.notification.gateway.NotificationTemplateProperties
import com.sportsapp.infrastructure.notification.gateway.TemplateRendererImpl
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty

class TemplateRendererImplTest : BehaviorSpec({

    val properties = NotificationTemplateProperties(
        templates = mapOf(
            "payment-completed" to NotificationTemplateProperties.TemplateDefinition(
                title = "결제 완료",
                body = "{amount}원 결제가 완료되었습니다.",
            ),
            "welcome" to NotificationTemplateProperties.TemplateDefinition(
                title = "환영합니다",
                body = "{userName}님, 반갑습니다.",
            ),
            "booking-confirmed" to NotificationTemplateProperties.TemplateDefinition(
                title = "예약 확정",
                body = "{facilityName} 예약이 확정되었습니다.",
            ),
            "ticket-issued" to NotificationTemplateProperties.TemplateDefinition(
                title = "티켓 발권 완료",
                body = "{eventTitle} 티켓이 발권되었습니다.",
            ),
        )
    )

    val renderer = TemplateRendererImpl(properties)

    Given("[U-01] payment-completed 템플릿 + amount=30000 payload") {
        When("render 를 호출하면") {
            val result = renderer.render("payment-completed", mapOf("amount" to 30000))

            Then("[U-01] 정확한 렌더 결과가 반환된다") {
                result.title shouldBe "결제 완료"
                result.body shouldBe "30000원 결제가 완료되었습니다."
            }
        }
    }

    Given("[U-02] 누락된 placeholder 키가 있는 payload") {
        When("render 를 호출하면") {
            val result = renderer.render("payment-completed", emptyMap())

            Then("[U-02] 누락된 placeholder 키는 빈 문자열로 치환된다") {
                result.body shouldBe "원 결제가 완료되었습니다."
            }
        }
    }

    Given("[U-03] 존재하지 않는 templateId") {
        When("render 를 호출하면") {
            Then("[U-03] UnknownTemplateException 이 발생한다") {
                shouldThrow<UnknownTemplateException> {
                    renderer.render("non-existent-template", emptyMap())
                }
            }
        }
    }

    Given("[U-04] 여러 placeholder 가 있는 welcome 템플릿") {
        When("모든 키를 payload 에 제공하면") {
            val result = renderer.render("welcome", mapOf("userName" to "홍길동"))

            Then("[U-04] 모든 placeholder 가 올바르게 치환된다") {
                result.body shouldBe "홍길동님, 반갑습니다."
            }
        }
    }

    Given("[U-05] 앞머리 placeholder 가 비어 렌더되는 booking-confirmed 템플릿") {
        When("변수를 전달하지 않고 render 를 호출하면") {
            val result = renderer.render("booking-confirmed", emptyMap())

            Then("본문 앞에 공백이 남지 않는다") {
                result.body shouldBe "예약이 확정되었습니다."
            }
        }
    }

    Given("[U-06] 문장 중간 placeholder 가 비어 렌더되는 템플릿") {
        val spacedRenderer = TemplateRendererImpl(
            NotificationTemplateProperties(
                templates = mapOf(
                    "spaced" to NotificationTemplateProperties.TemplateDefinition(
                        title = "제목",
                        body = "예약 {facilityName} 확정",
                    ),
                )
            )
        )

        When("변수를 전달하지 않고 render 를 호출하면") {
            val result = spacedRenderer.render("spaced", emptyMap())

            Then("이중 공백이 하나로 접힌다") {
                result.body shouldBe "예약 확정"
            }
        }
    }

    Given("[U-07] ticket-issued 템플릿 + eventTitle payload") {
        When("render 를 호출하면") {
            val result = renderer.render("ticket-issued", mapOf("eventTitle" to "2026 시티리그 4강 홈경기"))

            Then("티켓 발권 제목·본문이 렌더된다") {
                result.title shouldBe "티켓 발권 완료"
                result.body shouldBe "2026 시티리그 4강 홈경기 티켓이 발권되었습니다."
            }
        }
    }
})
