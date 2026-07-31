package com.sportsapp.domain.identity.vo

import com.sportsapp.domain.common.security.AuthenticatedPrincipal

/**
 * 파트너 API 키 검증 결과 — edge 가 소유하는 계약 (W1-06b).
 *
 * [PartnerIdentityVerificationOutcome.SUSPENDED] 만 별도 결과로 남긴다 — 오늘 필터가 이미 403 으로
 * 외부에 구분해 노출하는 유일한 실패다. 그 외(키 없음·해시 불일치·REVOKED·연동 유저 없음)는
 * 전부 [PartnerIdentityVerificationOutcome.INVALID] 로 수렴해 401 이 된다. 새로 노출되는 구분은 없다.
 */
data class PartnerIdentityVerification(
    val outcome: PartnerIdentityVerificationOutcome,
    val principal: AuthenticatedPrincipal?,
    val authorities: List<String>,
    val partnerId: Long?,
    val linkedUserId: Long?,
) {
    companion object {
        fun invalid(): PartnerIdentityVerification = rejected(PartnerIdentityVerificationOutcome.INVALID)

        fun suspended(): PartnerIdentityVerification = rejected(PartnerIdentityVerificationOutcome.SUSPENDED)

        fun valid(
            principal: AuthenticatedPrincipal,
            authorities: List<String>,
            partnerId: Long,
            linkedUserId: Long,
        ): PartnerIdentityVerification = PartnerIdentityVerification(
            outcome = PartnerIdentityVerificationOutcome.VALID,
            principal = principal,
            authorities = authorities,
            partnerId = partnerId,
            linkedUserId = linkedUserId,
        )

        private fun rejected(outcome: PartnerIdentityVerificationOutcome): PartnerIdentityVerification =
            PartnerIdentityVerification(
                outcome = outcome,
                principal = null,
                authorities = emptyList(),
                partnerId = null,
                linkedUserId = null,
            )
    }
}
