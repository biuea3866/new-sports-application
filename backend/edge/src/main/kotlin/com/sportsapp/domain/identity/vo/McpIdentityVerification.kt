package com.sportsapp.domain.identity.vo

import com.sportsapp.domain.common.security.AuthenticatedPrincipal

/**
 * MCP 토큰 검증 결과 — edge 가 소유하는 계약 (W1-06b).
 *
 * 실패 사유를 담지 않는다. 존재하지 않는 토큰·해시 불일치·비활성·만료가 모두 동일한 401 로 수렴해야
 * 공격자가 토큰 존재 여부를 구분할 수 없다 (platform `McpTokenVerification` 과 동일한 무누출 경계).
 *
 * [principal] 과 [authorities] 는 조립자(어댑터)가 채운다 — edge 는 구체 주체 타입을 모른다.
 */
data class McpIdentityVerification(
    val valid: Boolean,
    val principal: AuthenticatedPrincipal?,
    val authorities: List<String>,
    val subjectId: Long?,
    val scopes: List<String>,
) {
    companion object {
        fun invalid(): McpIdentityVerification = McpIdentityVerification(
            valid = false,
            principal = null,
            authorities = emptyList(),
            subjectId = null,
            scopes = emptyList(),
        )

        fun valid(
            principal: AuthenticatedPrincipal,
            authorities: List<String>,
            subjectId: Long,
            scopes: List<String>,
        ): McpIdentityVerification = McpIdentityVerification(
            valid = true,
            principal = principal,
            authorities = authorities,
            subjectId = subjectId,
            scopes = scopes,
        )
    }
}
