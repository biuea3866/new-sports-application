package com.sportsapp.infrastructure.community.gateway

import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.community.gateway.SlotInfo
import com.sportsapp.domain.community.gateway.SlotInfoGateway
import org.springframework.stereotype.Component

/**
 * [SlotInfoGateway] 구현체 (TDD B3, R1).
 *
 * community 도메인은 booking 도메인을 직접 import하지 않으며, 이 infrastructure 구현체만
 * booking의 [SlotRepository]를 읽어 community DTO([SlotInfo])로 변환한다.
 */
@Component
class SlotInfoGatewayImpl(
    private val slotRepository: SlotRepository,
) : SlotInfoGateway {

    override fun findBy(slotId: Long): SlotInfo? = slotRepository.findById(slotId)?.let { slot ->
        SlotInfo(
            facilityId = slot.facilityId,
            date = slot.date,
            timeRange = slot.timeRange,
            capacity = slot.capacity,
        )
    }
}
