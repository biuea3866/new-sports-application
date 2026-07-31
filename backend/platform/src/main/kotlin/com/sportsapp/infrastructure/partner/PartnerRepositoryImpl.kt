package com.sportsapp.infrastructure.partner

import com.sportsapp.domain.partner.entity.Partner
import com.sportsapp.domain.partner.exception.PartnerNotFoundException
import com.sportsapp.domain.partner.repository.PartnerRepository
import org.springframework.stereotype.Repository

@Repository
class PartnerRepositoryImpl(
    private val partnerJpaRepository: PartnerJpaRepository,
) : PartnerRepository {

    override fun save(partner: Partner): Partner {
        val jpaEntity = partner.id
            ?.let { id ->
                partnerJpaRepository.findById(id)
                    .orElseThrow { PartnerNotFoundException(id) }
                    .apply { applyFrom(partner) }
            }
            ?: PartnerJpaEntity.of(partner)
        return partnerJpaRepository.save(jpaEntity).toDomain()
    }

    override fun findById(partnerId: Long): Partner? =
        partnerJpaRepository.findById(partnerId).orElse(null)?.toDomain()

    override fun findByLinkedUserId(linkedUserId: Long): Partner? =
        partnerJpaRepository.findByLinkedUserId(linkedUserId)?.toDomain()
}
