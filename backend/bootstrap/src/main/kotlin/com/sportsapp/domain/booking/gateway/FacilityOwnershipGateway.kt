package com.sportsapp.domain.booking.gateway

/**
 * 슬롯이 참조하는 시설의 존재와 소유권을 booking 도메인 관점에서 검증하는 게이트웨이.
 *
 * booking 도메인은 facility 도메인을 직접 import 하지 않으며, 이 interface 를 통해서만
 * 시설 소유권을 확인한다. 구현체는 infrastructure 레이어에 위치한다.
 */
interface FacilityOwnershipGateway {
    /**
     * facilityId 시설이 존재하고 userId 가 그 소유자인지 검증한다.
     *
     * @throws com.sportsapp.domain.booking.exception.SlotFacilityNotFoundException 시설이 없을 때
     * @throws com.sportsapp.domain.booking.exception.UnauthorizedFacilityAccessException 소유자가 아닐 때
     */
    fun requireOwner(facilityId: String, userId: Long)
}
