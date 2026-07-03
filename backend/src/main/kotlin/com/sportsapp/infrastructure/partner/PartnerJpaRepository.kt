package com.sportsapp.infrastructure.partner

import org.springframework.data.jpa.repository.JpaRepository

interface PartnerJpaRepository : JpaRepository<PartnerJpaEntity, Long> {
    fun findByLinkedUserId(linkedUserId: Long): PartnerJpaEntity?
}
