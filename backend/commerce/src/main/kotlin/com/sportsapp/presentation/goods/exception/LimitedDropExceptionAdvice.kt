package com.sportsapp.presentation.goods.exception

import com.sportsapp.domain.goods.exception.LimitedDropTooEarlyException
import com.sportsapp.presentation.exception.ProblemDetailBuilder
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * [S2-02] `LimitedDropTooEarlyException`(commerce 소유)은 `com.sportsapp.domain.goods.exception`을
 * import 할 수 없는 `common`의 `GlobalExceptionHandler`에서 분리했다 — common → commerce
 * 역의존을 만들지 않기 위함이다.
 *
 * `LimitedDropTooEarlyException`은 `BusinessException`의 하위 타입이라, 이 advice 가 common 의
 * `GlobalExceptionHandler`(`@Order(Ordered.LOWEST_PRECEDENCE)`)보다 먼저 매칭되도록
 * `@Order(Ordered.HIGHEST_PRECEDENCE)`로 우선순위를 명시한다. 순서가 뒤집히면
 * `BusinessException` 상위 핸들러가 먼저 잡아 [LimitedDropTooEarlyException.openAt] 프로퍼티가
 * 응답에서 빠진다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class LimitedDropExceptionAdvice {

    @ExceptionHandler(LimitedDropTooEarlyException::class)
    fun handleLimitedDropTooEarlyException(exception: LimitedDropTooEarlyException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = exception.status,
            code = exception.errorCode,
            detail = exception.message,
        )
        problemDetail.setProperty("openAt", exception.openAt.toString())
        return ResponseEntity.status(exception.status.httpStatus).body(problemDetail)
    }
}
