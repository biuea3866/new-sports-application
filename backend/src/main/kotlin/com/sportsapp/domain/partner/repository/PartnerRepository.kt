package com.sportsapp.domain.partner.repository

import com.sportsapp.domain.partner.entity.Partner

interface PartnerRepository {
    fun save(partner: Partner): Partner
    fun findById(partnerId: Long): Partner?
    fun findByLinkedUserId(linkedUserId: Long): Partner?
}
