package com.sportsapp.infrastructure.facility.gateway

import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.facility.gateway.SlotQueryGateway
import org.springframework.stereotype.Component

/**
 * SlotQueryGateway 구현체.
 *
 * booking 도메인의 SlotRepository(MySQL) 를 사용해 시설의 활성 슬롯 존재 여부를 조회한다.
 */
@Component
class SlotQueryGatewayImpl(
    private val slotRepository: SlotRepository,
) : SlotQueryGateway {

    override fun hasActiveSlots(facilityId: String): Boolean =
        slotRepository.existsActiveByFacilityId(facilityId)
}
