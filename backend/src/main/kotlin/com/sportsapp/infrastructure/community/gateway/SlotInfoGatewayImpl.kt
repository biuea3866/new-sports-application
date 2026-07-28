package com.sportsapp.infrastructure.community.gateway

import com.sportsapp.domain.booking.service.SlotDomainService
import com.sportsapp.domain.community.gateway.SlotInfo
import com.sportsapp.domain.community.gateway.SlotInfoGateway
import org.springframework.stereotype.Component

/**
 * [SlotInfoGateway] 구현체 (TDD B3, R1) — booking 공급자 DomainService 경유.
 *
 * community 도메인은 booking 도메인을 직접 import하지 않으며, 이 infrastructure 구현체만
 * booking의 공개 행위 계약인 [SlotDomainService]를 호출해 community DTO([SlotInfo])로 변환한다.
 * booking의 `SlotRepository`(테이블)를 직접 알지 못한다.
 */
@Component
class SlotInfoGatewayImpl(
    private val slotDomainService: SlotDomainService,
) : SlotInfoGateway {

    override fun findBy(slotId: Long): SlotInfo? = slotDomainService.findBy(slotId)?.let { slot ->
        SlotInfo(
            facilityId = slot.facilityId,
            date = slot.date,
            timeRange = slot.timeRange,
            capacity = slot.capacity,
        )
    }
}
