package com.sportsapp.application.mcp.dto

import com.sportsapp.domain.mcp.vo.McpTokenVerification

/**
 * platform 내부 신원 검증 API(W1-06a) 응답. edge(W1-06b 이후)가 401/403 판단 근거로 쓴다.
 *
 * `valid=false`일 때 실패 사유를 별도로 노출하지 않는다 — 존재하지 않는 토큰과 만료된 토큰의
 * 응답이 동일해야 토큰 존재 여부가 새지 않는다([McpTokenVerification] 참고).
 */
data class VerifyMcpTokenResponse(
    val valid: Boolean,
    val tokenId: Long?,
    val userId: Long?,
    val scopes: List<String>,
) {
    companion object {
        fun of(verification: McpTokenVerification): VerifyMcpTokenResponse = VerifyMcpTokenResponse(
            valid = verification.valid,
            tokenId = verification.tokenId,
            userId = verification.userId,
            scopes = verification.scopes.map { it.asString() },
        )
    }
}
