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
 * 강제하지만, **이 값 객체 자신도 같은 불변조건을 `init` 블록에서 재검증한다(6차 재리뷰
 * p3-2)** — 애초에 값 객체로 분리한 목적(위치 인자 뒤바뀜 차단)과 달리, 생성자 파라미터
 * 순서가 그대로 뒤바뀌어도(`BookingExpiryTtlPolicy(60, 15)`처럼 named argument 없이 값만
 * 전도돼 호출돼도) 이 클래스만 보면 컴파일이 그대로 통과했다. `BookingExpiryProperties`가
 * 애플리케이션 레이어에서 이미 걸러준다는 사실이 KDoc에만 적힌 채로 이 타입 자체는
 * 불변식을 스스로 지키지 못하는 상태였다 — domain은 `application.booking.config`를 import할
 * 수 없어 `BookingExpiryProperties`를 직접 참조하지 못하므로(레이어 의존 방향), 이 타입이
 * 유일하게 domain 레이어에서 불변식을 강제할 수 있는 지점이다.
 */
data class BookingExpiryTtlPolicy(
    val ttlMinutes: Long,
    val readyTtlMinutes: Long,
) {
    init {
        require(readyTtlMinutes > ttlMinutes) {
            "readyTtlMinutes($readyTtlMinutes)는 ttlMinutes($ttlMinutes)보다 커야 한다 — " +
                "그렇지 않으면 결제 진행 중(live)인 예약이 빠른 TTL에 의해 오만료된다"
        }
    }
}
