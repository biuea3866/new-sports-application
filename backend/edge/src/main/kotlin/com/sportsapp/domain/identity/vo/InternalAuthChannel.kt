package com.sportsapp.domain.identity.vo

/**
 * edge 가 검증한 인증 채널 (W1-06b §6-3).
 *
 * 내부 헤더로 전파되는 값이며, 하위 서비스는 "어느 채널로 검증된 신원인가" 를 이 값으로만 판단한다.
 * JWT 는 §6-3 결정에 따라 각 서비스가 자체 검증하므로 여기에 없다 — edge 가 대신 검증하지 않는다.
 */
enum class InternalAuthChannel {
    MCP_TOKEN,
    PARTNER_API_KEY,
}
