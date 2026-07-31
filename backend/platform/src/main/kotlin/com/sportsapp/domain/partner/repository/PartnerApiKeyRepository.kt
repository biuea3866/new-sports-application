package com.sportsapp.domain.partner.repository

import com.sportsapp.domain.partner.entity.PartnerApiKey

interface PartnerApiKeyRepository {
    fun save(apiKey: PartnerApiKey): PartnerApiKey
    fun findById(keyId: Long): PartnerApiKey?

    /**
     * 재발급 시 구 키 조회용: 파트너의 현재 ACTIVE 키를 조회한다.
     */
    fun findActiveByPartnerId(partnerId: Long): PartnerApiKey?
}
