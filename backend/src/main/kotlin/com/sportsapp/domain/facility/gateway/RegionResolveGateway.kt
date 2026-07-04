package com.sportsapp.domain.facility.gateway

import com.sportsapp.domain.facility.vo.FacilityRegion

/**
 * 주소·시도 힌트로부터 행정표준코드(시도·시군구)를 해석하는 Gateway.
 * 구현체는 MySQL regions 마스터를 조회한다.
 *
 * 우선순위: [sidoHint](입력값) + 주소 파싱.
 * 파싱·조회에 실패해도 예외를 던지지 않고 [FacilityRegion.UNSPECIFIED]를 반환한다.
 */
interface RegionResolveGateway {
    fun resolve(address: String, sidoHint: String?): FacilityRegion
}
