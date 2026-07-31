package com.sportsapp.domain.recruitment.dto

/**
 * recruitment 만료 스위퍼의 두 TTL(분) 값을 하나로 묶은 값 객체
 * (`facility-booking`(W1-11c) `BookingExpiryTtlPolicy`와 동일한 이유).
 *
 * [com.sportsapp.domain.recruitment.service.RecruitmentDomainService.filterExpirable]가 인접한
 * 타입(Long) `ttlMinutes`/`readyTtlMinutes`를 나란히 파라미터로 받으면, 호출부가 위치 인자로
 * 뒤바뀌어 넘겨도 컴파일이 통과해 빠른/느린 TTL이 전도되는 오동작이 조용히 재발할 수 있다.
 * 하나의 값 객체로 묶어 위치 인자 자체를 구조적으로 없앤다.
 *
 * 불변조건(`readyTtlMinutes > ttlMinutes`)은
 * [com.sportsapp.application.recruitment.config.RecruitmentApplicationExpiryProperties]가 부팅
 * 시 `require()`로 강제하지만, 이 값 객체 자신도 같은 불변조건을 `init` 블록에서 재검증한다 —
 * domain은 `application.recruitment.config`를 import할 수 없어(레이어 의존 방향) 그 타입을
 * 직접 참조하지 못하므로, 이 타입이 유일하게 domain 레이어에서 불변식을 강제할 수 있는 지점이다.
 */
data class ApplicationExpiryTtlPolicy(
    val ttlMinutes: Long,
    val readyTtlMinutes: Long,
) {
    init {
        require(readyTtlMinutes > ttlMinutes) {
            "readyTtlMinutes($readyTtlMinutes)는 ttlMinutes($ttlMinutes)보다 커야 한다 — " +
                "그렇지 않으면 결제 진행 중(live)인 신청이 빠른 TTL에 의해 오만료된다"
        }
    }
}
