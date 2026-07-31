package com.sportsapp.domain.mcp.vo

/**
 * MCP 토큰 검증 결과 값 객체 (W1-06a).
 *
 * `McpTokenAuthenticationFilter`(bootstrap)는 검증 실패 사유(존재하지 않음·해시 불일치·
 * 비활성·만료)를 구분하지 않고 항상 동일한 401 메시지로 응답한다 — 이 값 객체도 그 무누출
 * 경계를 그대로 보존해 `valid=false` 외에 실패 사유를 노출하지 않는다. 존재하지 않는 토큰과
 * 만료된 토큰의 응답이 동일해야 공격자가 토큰 존재 여부를 구분할 수 없다.
 */
data class McpTokenVerification(
    val valid: Boolean,
    val tokenId: Long?,
    val userId: Long?,
    val scopes: Set<McpScope>,
) {
    companion object {
        fun invalid(): McpTokenVerification = McpTokenVerification(
            valid = false,
            tokenId = null,
            userId = null,
            scopes = emptySet(),
        )

        fun valid(tokenId: Long, userId: Long, scopes: Set<McpScope>): McpTokenVerification = McpTokenVerification(
            valid = true,
            tokenId = tokenId,
            userId = userId,
            scopes = scopes,
        )
    }
}
