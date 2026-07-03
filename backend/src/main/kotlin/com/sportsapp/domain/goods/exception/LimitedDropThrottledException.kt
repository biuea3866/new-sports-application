package com.sportsapp.domain.goods.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 완충 permit(FR-7)을 획득하지 못해 [com.sportsapp.domain.goods.gateway.DropReservationStore.reserve]가
 * [com.sportsapp.domain.goods.gateway.ReservationResult.Throttled]를 반환하면 던진다. DB에는 도달하지 않는다.
 */
class LimitedDropThrottledException(dropId: Long) : BusinessException(
    errorCode = "LIMITED_DROP_THROTTLED",
    message = "요청이 몰려 대기 처리되었습니다: dropId=$dropId"
) {
    override val status: ErrorStatus = ErrorStatus.TOO_MANY_REQUESTS
}
