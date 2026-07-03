package com.sportsapp.domain.goods.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import java.time.ZonedDateTime

/**
 * 판매 시작 전(now < openAt) 구매를 시도했을 때 던진다 (FR-2 게이트).
 *
 * API 계약상 HTTP 425 Too Early로 매핑돼야 하나, [ErrorStatus]에 425가 아직 정의되지
 * 않아 임시로 [ErrorStatus.UNPROCESSABLE](422)를 사용한다. presentation 레이어 구현
 * 티켓에서 `ErrorStatus.TOO_EARLY(425)` 추가가 필요하다 (domain/common 수정 필요, 이 티켓 범위 밖).
 */
class LimitedDropTooEarlyException(
    dropId: Long,
    val openAt: ZonedDateTime,
) : BusinessException(
    errorCode = "LIMITED_DROP_TOO_EARLY",
    message = "판매 시작 전입니다: dropId=$dropId, openAt=$openAt"
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
