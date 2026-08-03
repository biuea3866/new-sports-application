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

            // 렌더러는 템플릿 원문을 그대로 치환한다 — 공백 정리는 표시 계층
            // (Notification#payloadText 의 trim)이 담당한다. 여기서 전역 정규화를 하면
            // email 등 여러 줄 본문의 들여쓰기까지 뭉개진다.
            Then("치환 결과를 가공 없이 돌려준다") {
                result.body shouldBe " 예약이 확정되었습니다."
            }
        }
    }

    Given("[U-06] 여러 줄 들여쓰기가 있는 email 본문 템플릿") {
        val multilineRenderer = TemplateRendererImpl(
            NotificationTemplateProperties(
                templates = mapOf(
                    "email-notice" to NotificationTemplateProperties.TemplateDefinition(
                        title = "안내",
                        body = "안녕하세요 {userName}님,\n    들여쓴 안내문입니다.",
                    ),
                )
            )
        )

        When("render 를 호출하면") {
            val result = multilineRenderer.render("email-notice", mapOf("userName" to "홍길동"))

            Then("여러 줄 본문의 들여쓰기가 보존된다") {
                result.body shouldBe "안녕하세요 홍길동님,\n    들여쓴 안내문입니다."
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
