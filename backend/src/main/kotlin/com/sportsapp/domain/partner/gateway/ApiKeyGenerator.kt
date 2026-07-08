package com.sportsapp.domain.partner.gateway

/**
 * API Key 랜덤값 생성·해시 계약. 외부 시스템 호출이 아니라 순수 유틸(BCrypt 등) 위임이지만
 * 인프라 구현이 필요해 domain interface + infrastructure 구현 패턴을 따른다.
 */
interface ApiKeyGenerator {
    fun generateRandomPart(): String
    fun hash(plainKey: String): String
    fun matches(plainKey: String, keyHash: String): Boolean
}
