package com.sportsapp.domain.goods.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import java.time.ZonedDateTime

/**
 * 판매 시작 전(now < openAt) 구매를 시도했을 때 던진다 (FR-2 게이트).
 *
 * API 계약상 HTTP 425 Too Early로 매핑된다 (`ErrorStatus.TOO_EARLY`). [openAt]은
 * presentation 레이어가 응답 본문에 포함해 클라이언트가 재시도 시점을 판단하게 한다.
 */
class LimitedDropTooEarlyException(
    dropId: Long,
    val openAt: ZonedDateTime,
) : BusinessException(
    errorCode = "LIMITED_DROP_TOO_EARLY",
    message = "판매 시작 전입니다: dropId=$dropId, openAt=$openAt"
) {
    override val status: ErrorStatus = ErrorStatus.TOO_EARLY
}
