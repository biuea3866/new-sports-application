package com.sportsapp.domain.goods.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 완충 permit(FR-7)을 [com.sportsapp.domain.goods.gateway.DropReservationStore.tryAcquireThrottle]로
 * 획득하지 못하면 던진다. Redis 판정(Admitted·AlreadyReserved·fail-open) 결과와 무관하게 DB 쓰기
 * 직전에 판정되므로, DB에는 도달하지 않는다.
 */
class LimitedDropThrottledException(dropId: Long) : BusinessException(
    errorCode = "LIMITED_DROP_THROTTLED",
    message = "요청이 몰려 대기 처리되었습니다: dropId=$dropId"
) {
    override val status: ErrorStatus = ErrorStatus.TOO_MANY_REQUESTS
}
