package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.domain.booking.Slot
import com.sportsapp.domain.booking.SlotRepository
import org.springframework.stereotype.Repository

@Repository
class SlotRepositoryImpl(
    private val slotJpaRepository: SlotJpaRepository,
) : SlotRepository {

    override fun save(slot: Slot): Slot =
        slotJpaRepository.save(SlotJpaEntity.fromDomain(slot)).toDomain()

    override fun findById(id: Long): Slot? =
        slotJpaRepository.findById(id).orElse(null)?.toDomain()
}
