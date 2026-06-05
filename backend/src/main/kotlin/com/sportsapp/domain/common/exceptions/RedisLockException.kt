package com.sportsapp.domain.common.exceptions

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * Redis 분산 락 작업 중 인프라 장애 발생 시 던진다.
 * INFRA-07 의 `BusinessException` 베이스를 상속해 `GlobalExceptionHandler` 가 일관된 ProblemDetail 응답으로 변환한다.
 */
class RedisLockException(
    message: String,
    cause: Throwable? = null,
) : BusinessException(
    errorCode = "REDIS_LOCK_FAILURE",
    message = message,
    cause = cause,
) {
    override val status: ErrorStatus = ErrorStatus.INTERNAL
}
