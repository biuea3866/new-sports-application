package com.sportsapp.infrastructure.facility.gateway

import com.sportsapp.domain.booking.service.SlotDomainService
import com.sportsapp.domain.facility.gateway.SlotQueryGateway
import org.springframework.stereotype.Component

/**
 * SlotQueryGateway 구현체 — booking 공급자 DomainService 경유.
 *
 * booking 도메인의 공개 행위 계약인 [SlotDomainService]를 통해 시설의 활성 슬롯 존재 여부를
 * 조회한다. booking의 `SlotRepository`(테이블)를 직접 알지 못한다.
 */
@Component
class SlotQueryGatewayImpl(
    private val slotDomainService: SlotDomainService,
) : SlotQueryGateway {

    override fun hasActiveSlots(facilityId: String): Boolean =
        slotDomainService.hasActiveSlots(facilityId)
}
