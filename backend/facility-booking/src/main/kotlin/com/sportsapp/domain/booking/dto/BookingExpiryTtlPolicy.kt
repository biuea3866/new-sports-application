package com.sportsapp.domain.booking.dto

/**
 * booking 만료 스위퍼의 두 TTL(분) 값을 하나로 묶은 값 객체(6차 재리뷰 — p3).
 *
 * [com.sportsapp.domain.booking.service.BookingDomainService.filterExpirable]가 인접한
 * Long 타입 `ttlMinutes`/`readyTtlMinutes`를 나란히 파라미터로 받으면, 호출부가 위치 인자로
 * 뒤바뀌어 넘겨도 컴파일이 통과해 빠른/느린 TTL이 전도되는 오동작이 조용히 재발할 수 있다
 * (`findExpirableBookingCandidates(ttlMinutes, afterId, ...)`에서 지적된 TTL↔커서 뒤바뀜과
 * 같은 유형의 위험). 하나의 값 객체로 묶어 위치 인자 자체를 구조적으로 없앤다.
 *
 * 불변조건(`readyTtlMinutes > ttlMinutes`)은
 * [com.sportsapp.application.booking.config.BookingExpiryProperties]가 부팅 시 `require()`로
 * 이미 강제한다 — 이 값 객체는 그 값을 domain 레이어까지 그대로 옮겨 담을 뿐 재검증하지
 * 않는다(application이 domain보다 먼저 걸러진 값을 넘긴다는 전제. domain은
 * `application.booking.config`를 import할 수 없어 `BookingExpiryProperties`를 직접
 * 참조하지 못한다 — 레이어 의존 방향).
 */
data class BookingExpiryTtlPolicy(
    val ttlMinutes: Long,
    val readyTtlMinutes: Long,
)
