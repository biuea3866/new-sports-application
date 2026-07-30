package com.sportsapp.domain.goods.gateway

/**
 * 한정판 예약(reserve.lua Admitted) 보상 계약 (FIX-02).
 *
 * [com.sportsapp.domain.goods.service.LimitedDropDomainService.admit]이 Redis 슬롯을 실제로
 * 차감한 시점(Admitted) 직후 호출해, 진행 중인 트랜잭션이 최종적으로 롤백으로 끝나면 예약을
 * 취소하도록 등록한다. 커밋되면 아무 것도 하지 않는다.
 *
 * `PurchaseLimitedDropUseCase`의 `@Transactional`은 UseCase 경계에 있고 커밋은 그 메서드가
 * 반환된 뒤 이루어지므로, `persistWithThrottle`의 동기 try/catch는 커밋 단계에서 지연 발생하는
 * 실패(예: `Stock` `@Version` UPDATE 충돌)를 잡지 못한다 — 이 계약이 그 간극을 메운다.
 *
 * "최종적으로"의 의미: `@Retryable`(재시도 예산)로 재시도가 남아 있는 중간 롤백에서는 취소하지
 * 않는다 — 취소하면 예약을 풀고 다음 시도가 다시 예약을 잡는 왕복이 생겨, Admitted였던 사용자가
 * 재시도 중 슬롯을 다른 사용자에게 빼앗길 수 있다. 재시도 예산이 소진되는 마지막 시도의 롤백에서만
 * 실제로 취소가 실행된다 — 구현체가 재시도 예산과 현재 시도 횟수를 대조해 판단한다.
 */
interface DropReservationCompensator {

    /**
     * @param admittedThisAttempt 이번 시도가 [com.sportsapp.domain.goods.gateway.ReservationResult.Admitted]로
     * Redis 슬롯을 새로 차감했는지 여부. `true`면 이번 재시도 시퀀스가 "내 예약"임을 표시한다.
     * `false`(AlreadyReserved)면, 같은 재시도 시퀀스 안에서 이미 그 표시가 되어 있을 때만 등록을
     * 이어간다 — 서로 다른 외부 호출이 우연히 같은 idempotencyKey로 AlreadyReserved를 받는 경우까지
     * 등록하면 남의 예약을 취소할 위험이 있다.
     */
    fun registerCancelOnRollback(
        dropId: Long,
        userId: Long,
        quantity: Int,
        idempotencyKey: String,
        admittedThisAttempt: Boolean,
    )
}
