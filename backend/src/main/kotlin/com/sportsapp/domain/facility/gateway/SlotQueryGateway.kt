package com.sportsapp.domain.facility.gateway

/**
 * 시설에 활성 슬롯이 존재하는지 facility 도메인 관점에서 질의하는 게이트웨이.
 *
 * facility 도메인은 booking 도메인을 직접 import 하지 않으며, 이 interface 를 통해서만
 * 슬롯 존재 여부를 확인한다. 구현체는 infrastructure 레이어에 위치한다.
 */
interface SlotQueryGateway {
    fun hasActiveSlots(facilityId: String): Boolean
}
