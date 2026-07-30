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
 * "최종적으로"의 의미: 이 호출을 감싸는 재시도 시퀀스가 더 이상 재시도하지 않는다고 확정되는
 * 시점에만 실제로 취소가 실행된다 — 재시도가 남아 있는 중간 롤백에서 취소하면 예약을 풀고 다음
 * 시도가 다시 예약을 잡는 왕복이 생겨, Admitted였던 사용자가 재시도 중 슬롯을 다른 사용자에게
 * 빼앗길 수 있다. "더 이상 재시도가 없다"의 판단 방법(사용 중인 재시도 프레임워크와의 연동)은
 * 구현체(infrastructure)의 책임이며, 이 계약은 그 판단 결과에만 의존한다.
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
