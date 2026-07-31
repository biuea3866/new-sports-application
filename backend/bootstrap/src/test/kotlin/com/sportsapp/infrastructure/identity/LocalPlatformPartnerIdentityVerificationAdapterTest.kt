package com.sportsapp.infrastructure.identity

import com.sportsapp.application.partner.dto.VerifyPartnerApiKeyCommand
import com.sportsapp.application.partner.dto.VerifyPartnerApiKeyResponse
import com.sportsapp.application.partner.usecase.VerifyPartnerApiKeyUseCase
import com.sportsapp.domain.common.security.UserPrincipal
import com.sportsapp.domain.identity.vo.PartnerApiCallActivity
import com.sportsapp.domain.identity.vo.PartnerIdentityVerificationOutcome
import com.sportsapp.domain.partner.gateway.PartnerActivityRecorder
import com.sportsapp.domain.partner.gateway.PartnerApiKeyUsageRecorder
import com.sportsapp.domain.partner.service.PartnerApiKeyVerificationFailure
import com.sportsapp.domain.user.dto.UserWithRoles
import com.sportsapp.domain.user.entity.UserStatus
import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime

class LocalPlatformPartnerIdentityVerificationAdapterTest : BehaviorSpec({

    val verifyPartnerApiKeyUseCase = mockk<VerifyPartnerApiKeyUseCase>()
    val userDomainService = mockk<UserDomainService>()
    val partnerApiKeyUsageRecorder = mockk<PartnerApiKeyUsageRecorder>()
    val partnerActivityRecorder = mockk<PartnerActivityRecorder>()
    val adapter = LocalPlatformPartnerIdentityVerificationAdapter(
        verifyPartnerApiKeyUseCase = verifyPartnerApiKeyUseCase,
        userDomainService = userDomainService,
        partnerApiKeyUsageRecorder = partnerApiKeyUsageRecorder,
        partnerActivityRecorder = partnerActivityRecorder,
    )

    fun platformResponse(
        valid: Boolean,
        failureReason: PartnerApiKeyVerificationFailure? = null,
    ): VerifyPartnerApiKeyResponse = VerifyPartnerApiKeyResponse(
        valid = valid,
        partnerId = if (valid) 1L else null,
        linkedUserId = if (valid) 10L else null,
        failureReason = failureReason,
    )

    Given("platform 이 유효 응답을 주면") {
        val plainKey = "partner_1_validsecret"
        every { verifyPartnerApiKeyUseCase.execute(VerifyPartnerApiKeyCommand(plainKey)) } returns
            platformResponse(valid = true)
        every { userDomainService.findByIdWithRoles(10L) } returns UserWithRoles(
            userId = 10L,
            email = "partner-10@sportsapp.com",
            status = UserStatus.ACTIVE,
            roleNames = listOf("USER", "GOODS_SELLER"),
            joinedAt = ZonedDateTime.now(),
        )

        When("검증하면") {
            val verification = adapter.verify(plainKey)

            Then("연동 유저 주체가 partnerAuthenticated 로 만들어진다 (인증 채널 신호 보존)") {
                verification.outcome shouldBe PartnerIdentityVerificationOutcome.VALID
                val principal = verification.principal
                principal.shouldBeInstanceOf<UserPrincipal>()
                principal.id shouldBe 10L
                principal.email shouldBe "partner-10@sportsapp.com"
                principal.roles shouldContainExactly listOf("USER", "GOODS_SELLER")
                principal.partnerAuthenticated shouldBe true
            }

            Then("권한은 역할별 ROLE_ 권한이다 (이동 전 필터와 동일)") {
                verification.authorities shouldContainExactly listOf("ROLE_USER", "ROLE_GOODS_SELLER")
            }

            Then("partnerId·linkedUserId 가 감사 기록용으로 함께 실린다") {
                verification.partnerId shouldBe 1L
                verification.linkedUserId shouldBe 10L
            }
        }
    }

    Given("platform 이 정지 응답을 주면") {
        val plainKey = "partner_2_suspended"
        every { verifyPartnerApiKeyUseCase.execute(VerifyPartnerApiKeyCommand(plainKey)) } returns
            platformResponse(valid = false, failureReason = PartnerApiKeyVerificationFailure.SUSPENDED)

        When("검증하면") {
            val verification = adapter.verify(plainKey)

            Then("정지 판정으로 매핑되고 연동 유저를 조회하지 않는다") {
                verification.outcome shouldBe PartnerIdentityVerificationOutcome.SUSPENDED
                verification.principal.shouldBeNull()
                verify(exactly = 0) { userDomainService.findByIdWithRoles(2L) }
            }
        }
    }

    Given("platform 이 무효 응답을 주면") {
        val plainKey = "partner_3_invalid"
        every { verifyPartnerApiKeyUseCase.execute(VerifyPartnerApiKeyCommand(plainKey)) } returns
            platformResponse(valid = false, failureReason = PartnerApiKeyVerificationFailure.INVALID)

        When("검증하면") {
            val verification = adapter.verify(plainKey)

            Then("무효 판정으로 매핑된다") {
                verification.outcome shouldBe PartnerIdentityVerificationOutcome.INVALID
                verification.principal.shouldBeNull()
            }
        }
    }

    Given("키 사용 기록을 요청하면") {
        justRun { partnerApiKeyUsageRecorder.recordUsage(1L) }

        When("recordUsage 를 호출하면") {
            adapter.recordUsage(1L)

            Then("platform recorder 에 위임한다") {
                verify(exactly = 1) { partnerApiKeyUsageRecorder.recordUsage(1L) }
            }
        }
    }

    Given("호출 감사 기록을 요청하면") {
        val activity = PartnerApiCallActivity(
            partnerId = 1L,
            userId = 10L,
            httpMethod = "POST",
            requestPath = "/goods-orders",
            statusCode = 201,
            latencyMs = 42,
            ipAddr = "10.0.0.7",
            userAgent = "partner-sdk/1.0",
            calledAt = ZonedDateTime.now(),
        )
        val captured = slot<ZonedDateTime>()
        justRun {
            partnerActivityRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), capture(captured))
        }

        When("recordActivity 를 호출하면") {
            adapter.recordActivity(activity)

            Then("값 객체가 platform recorder 시그니처로 풀려 전달된다") {
                verify(exactly = 1) {
                    partnerActivityRecorder.record(
                        partnerId = 1L,
                        userId = 10L,
                        httpMethod = "POST",
                        requestPath = "/goods-orders",
                        statusCode = 201,
                        latencyMs = 42,
                        ipAddr = "10.0.0.7",
                        userAgent = "partner-sdk/1.0",
                        calledAt = activity.calledAt,
                    )
                }
            }

            Then("필터가 계측한 호출 시각이 그대로 전달된다 — 기록 시점으로 덮어쓰지 않는다") {
                captured.captured shouldBe activity.calledAt
            }
        }
    }
})
