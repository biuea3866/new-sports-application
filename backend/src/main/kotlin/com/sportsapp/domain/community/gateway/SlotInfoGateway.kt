package com.sportsapp.domain.community.gateway

import java.time.ZonedDateTime

/**
 * community가 booking Slot의 표시정보(시설·일시·정원)를 조회하는 ACL 게이트웨이 (TDD B3, R1).
 *
 * community 도메인은 booking 도메인을 직접 import하지 않으며, 이 interface를 통해서만
 * 슬롯 표시정보를 확인한다. 구현체는 infrastructure 레이어에 위치하며 booking의
 * SlotRepository를 읽는다. 정원(capacity)은 Slot이 소유하며 community는 그대로 노출만 한다.
 */
interface SlotInfoGateway {
    /** [slotId] 슬롯의 표시정보를 조회한다. 슬롯이 없으면 null을 반환한다(예외 전파 금지). */
    fun findBy(slotId: Long): SlotInfo?
}

/** community 관점의 슬롯 표시 DTO — booking `Slot` 엔티티를 노출하지 않기 위한 ACL 변환 결과. */
data class SlotInfo(
    val facilityId: String,
    val date: ZonedDateTime,
    val timeRange: String,
    val capacity: Int,
)
