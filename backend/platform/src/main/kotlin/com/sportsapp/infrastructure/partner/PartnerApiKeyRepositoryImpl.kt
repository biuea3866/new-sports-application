package com.sportsapp.infrastructure.partner

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.partner.entity.ApiKeyStatus
import com.sportsapp.domain.partner.entity.PartnerApiKey
import com.sportsapp.domain.partner.repository.PartnerApiKeyRepository
import org.springframework.stereotype.Repository

@Repository
class PartnerApiKeyRepositoryImpl(
    private val partnerApiKeyJpaRepository: PartnerApiKeyJpaRepository,
) : PartnerApiKeyRepository {

    override fun save(apiKey: PartnerApiKey): PartnerApiKey {
        val jpaEntity = apiKey.id
            ?.let { id ->
                partnerApiKeyJpaRepository.findById(id)
                    .orElseThrow { ResourceNotFoundException("PartnerApiKey", id) }
                    .apply { applyFrom(apiKey) }
            }
            ?: PartnerApiKeyJpaEntity.of(apiKey)
        return partnerApiKeyJpaRepository.save(jpaEntity).toDomain()
    }

    override fun findById(keyId: Long): PartnerApiKey? =
        partnerApiKeyJpaRepository.findById(keyId).orElse(null)?.toDomain()

    override fun findActiveByPartnerId(partnerId: Long): PartnerApiKey? =
        partnerApiKeyJpaRepository.findByPartnerIdAndStatus(partnerId, ApiKeyStatus.ACTIVE)?.toDomain()
}
