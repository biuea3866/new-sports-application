package com.sportsapp.infrastructure.identity

import com.sportsapp.application.partner.dto.VerifyPartnerApiKeyCommand
import com.sportsapp.application.partner.dto.VerifyPartnerApiKeyResponse
import com.sportsapp.application.partner.usecase.VerifyPartnerApiKeyUseCase
import com.sportsapp.domain.common.security.UserPrincipal
import com.sportsapp.domain.identity.gateway.PlatformPartnerIdentityVerificationGateway
import com.sportsapp.domain.identity.vo.PartnerApiCallActivity
import com.sportsapp.domain.identity.vo.PartnerIdentityVerification
import com.sportsapp.domain.partner.gateway.PartnerActivityRecorder
import com.sportsapp.domain.partner.gateway.PartnerApiKeyUsageRecorder
import com.sportsapp.domain.partner.service.PartnerApiKeyVerificationFailure
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Component

/**
 * 1단계 로컬 어댑터 — edge 의 파트너 신원 검증 계약을 platform UseCase·recorder 호출로 만족시킨다.
 *
 * MCP 어댑터와 달리 `@Profile` 게이트가 없다 — 이동 전 `PartnerApiKeyAuthenticationFilter` 도
 * 게이트 없이 `test-jpa` 에서 빈으로 존재했고, 그 경계를 그대로 보존한다.
 * 2단계에는 edge 안의 RestClient 구현이 `/internal/partner-api-keys/verify` 를 호출한다.
 */
@Component
class LocalPlatformPartnerIdentityVerificationAdapter(
    private val verifyPartnerApiKeyUseCase: VerifyPartnerApiKeyUseCase,
    private val userDomainService: UserDomainService,
    private val partnerApiKeyUsageRecorder: PartnerApiKeyUsageRecorder,
    private val partnerActivityRecorder: PartnerActivityRecorder,
) : PlatformPartnerIdentityVerificationGateway {

    override fun verify(plainKey: String): PartnerIdentityVerification {
        val response = verifyPartnerApiKeyUseCase.execute(VerifyPartnerApiKeyCommand(plainKey))
        if (!response.valid) return toRejection(response)
        return toVerification(response)
    }

    override fun recordUsage(keyId: Long) = partnerApiKeyUsageRecorder.recordUsage(keyId)

    override fun recordActivity(activity: PartnerApiCallActivity) = partnerActivityRecorder.record(
        partnerId = activity.partnerId,
        userId = activity.userId,
        httpMethod = activity.httpMethod,
        requestPath = activity.requestPath,
        statusCode = activity.statusCode,
        latencyMs = activity.latencyMs,
        ipAddr = activity.ipAddr,
        userAgent = activity.userAgent,
        calledAt = activity.calledAt,
    )

    /**
     * 연동 유저 조회는 검증 성공 후에만 한다 — 이동 전 필터와 동일한 순서·트랜잭션 경계이며,
     * 유저가 없으면 예외가 그대로 전파된다(이동 전 동작 보존).
     */
    private fun toVerification(response: VerifyPartnerApiKeyResponse): PartnerIdentityVerification {
        val linkedUserId = requireNotNull(response.linkedUserId) { "verified partner key must carry linkedUserId" }
        val linkedUser = userDomainService.findByIdWithRoles(linkedUserId)
        return PartnerIdentityVerification.valid(
            principal = UserPrincipal(
                id = linkedUserId,
                email = linkedUser.email,
                roles = linkedUser.roleNames,
                partnerAuthenticated = true,
            ),
            authorities = linkedUser.roleNames.map { "ROLE_$it" },
            partnerId = requireNotNull(response.partnerId) { "verified partner key must carry partnerId" },
            linkedUserId = linkedUserId,
        )
    }

    private fun toRejection(response: VerifyPartnerApiKeyResponse): PartnerIdentityVerification =
        when (response.failureReason) {
            PartnerApiKeyVerificationFailure.SUSPENDED -> PartnerIdentityVerification.suspended()
            else -> PartnerIdentityVerification.invalid()
        }
}
