package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.domain.booking.Slot
import com.sportsapp.domain.booking.SlotRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class SlotRepositoryImpl(
    private val slotJpaRepository: SlotJpaRepository,
) : SlotRepository {

    override fun save(slot: Slot): Slot =
        slotJpaRepository.save(slot)

    override fun findById(id: Long): Slot? =
        slotJpaRepository.findByIdOrNull(id)
}
