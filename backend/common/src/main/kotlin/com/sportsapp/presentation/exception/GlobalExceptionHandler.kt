package com.sportsapp.presentation.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorizationDeniedException
import jakarta.validation.ConstraintViolationException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.QueryTimeoutException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * [S2-02] `common` 소유 공용 예외 어드바이스. `LimitedDropTooEarlyException`(commerce 소유)은
 * common → commerce 역의존을 만들지 않기 위해 이 클래스에서 제외했다 — commerce 자신의
 * `LimitedDropExceptionAdvice`가 처리한다. 두 advice 는 `LimitedDropTooEarlyException`이
 * `BusinessException`의 하위 타입이라 순서에 민감하므로, 이 클래스는 명시적으로
 * `Ordered.LOWEST_PRECEDENCE`(가장 낮은 우선순위 — 다른 더 구체적인 advice 가 먼저 매칭되게
 * 양보)를 선언한다.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
// TooManyFunctions 억제 근거(W1-DEBT-01): 예외 타입별 @ExceptionHandler 디스패치 테이블이다.
// 함수 수 = 처리하는 예외 종류 수이고, 쪼개면 @RestControllerAdvice 가 여러 개로 늘어나 우선순위가
// 불명확해진다. common testFixtures 의 미러 구현도 같은 사유로 억제돼 있다.
@Suppress("TooManyFunctions")
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(exception: BusinessException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = exception.status,
            code = exception.errorCode,
            detail = exception.message
        )
        return ResponseEntity.status(exception.status.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(AuthorizationDeniedException::class)
    fun handleAuthorizationDeniedException(exception: AuthorizationDeniedException): ResponseEntity<ProblemDetail> {
        logger.debug("Authorization denied: {}", exception.message)
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.FORBIDDEN,
            code = "FORBIDDEN",
            detail = "Access denied"
        )
        return ResponseEntity.status(ErrorStatus.FORBIDDEN.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(exception: ConstraintViolationException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            detail = "Request parameter validation failed"
        )
        return ResponseEntity.status(ErrorStatus.BAD_REQUEST.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(exception: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            detail = "Request validation failed"
        )
        val fieldErrors = exception.bindingResult.fieldErrors.map { fieldError ->
            mapOf(
                "field" to fieldError.field,
                "message" to (fieldError.defaultMessage ?: "invalid value")
            )
        }
        problemDetail.setProperty("fieldErrors", fieldErrors)
        return ResponseEntity.status(ErrorStatus.BAD_REQUEST.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingRequestHeaderException(exception: MissingRequestHeaderException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.BAD_REQUEST,
            code = "MISSING_REQUEST_HEADER",
            detail = "Required header is missing: ${exception.headerName}"
        )
        return ResponseEntity.status(ErrorStatus.BAD_REQUEST.httpStatus).body(problemDetail)
    }

    /**
     * [F6] 필수 @RequestParam 누락 시 던져지는 예외. 매핑이 없으면 generic Exception 핸들러(500)로
     * 떨어진다 — MissingRequestHeaderException과 동일한 패턴으로 400 처리한다.
     */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameterException(
        exception: MissingServletRequestParameterException,
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.BAD_REQUEST,
            code = "MISSING_REQUEST_PARAMETER",
            detail = "Required request parameter is missing: ${exception.parameterName}"
        )
        return ResponseEntity.status(ErrorStatus.BAD_REQUEST.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(
        exception: MethodArgumentTypeMismatchException,
    ): ResponseEntity<ProblemDetail> {
        val paramName = exception.name
        val invalidValue = exception.value
        val requiredType = exception.requiredType?.simpleName ?: "unknown"
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.BAD_REQUEST,
            code = "INVALID_ENUM_VALUE",
            detail = "Invalid value '$invalidValue' for parameter '$paramName'. Expected type: $requiredType",
        )
        return ResponseEntity.status(ErrorStatus.BAD_REQUEST.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    // UnusedParameter 억제 근거(W1-DEBT-01): @ExceptionHandler 는 **파라미터 타입으로 디스패치**한다.
    // 본문에서 예외 값을 쓰지 않아도 시그니처에서 뺄 수 없다 — 빼는 순간 이 핸들러가 매칭되지 않는다.
    @Suppress("UnusedParameter")
    fun handleMessageNotReadableException(exception: HttpMessageNotReadableException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.BAD_REQUEST,
            code = "MALFORMED_REQUEST_BODY",
            detail = "Request body is malformed or contains an invalid value"
        )
        return ResponseEntity.status(ErrorStatus.BAD_REQUEST.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(exception: IllegalArgumentException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.BAD_REQUEST,
            code = "BAD_REQUEST",
            detail = exception.message ?: "Invalid request"
        )
        return ResponseEntity.status(ErrorStatus.BAD_REQUEST.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(exception: AccessDeniedException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN)
        problemDetail.detail = exception.message ?: "Access denied"
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail)
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLockException(exception: ObjectOptimisticLockingFailureException): ResponseEntity<ProblemDetail> {
        logger.debug("Optimistic lock conflict: {}", exception.message)
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.CONFLICT,
            code = "OPTIMISTIC_LOCK_CONFLICT",
            detail = "Resource was modified concurrently. Please retry."
        )
        return ResponseEntity.status(ErrorStatus.CONFLICT.httpStatus).body(problemDetail)
    }

    /**
     * 매핑된 컨트롤러도, 정적 리소스도 없는 요청(예: 피처 플래그로 컨트롤러 빈이 제거된 경로)에
     * Spring 6.1+ [ResourceHttpRequestHandler]가 던지는 예외. 하위 [Exception] 핸들러가 catch-all이라
     * 별도 처리하지 않으면 500으로 변환되므로, 404로 명시 매핑한다.
     */
    @ExceptionHandler(NoResourceFoundException::class)
    // UnusedParameter 억제 근거(W1-DEBT-01): @ExceptionHandler 는 **파라미터 타입으로 디스패치**한다.
    // 본문에서 예외 값을 쓰지 않아도 시그니처에서 뺄 수 없다 — 빼는 순간 이 핸들러가 매칭되지 않는다.
    @Suppress("UnusedParameter")
    fun handleNoResourceFoundException(exception: NoResourceFoundException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.NOT_FOUND,
            code = "NOT_FOUND",
            detail = "Requested resource does not exist"
        )
        return ResponseEntity.status(ErrorStatus.NOT_FOUND.httpStatus).body(problemDetail)
    }

    /**
     * [FIX-03] HikariCP 커넥션 풀 고갈 — `@Transactional` 시작 시점에 JpaTransactionManager가
     * 커넥션을 얻지 못하면 이 예외로 감싼다 (실측 리포트 L77-83, 구 기본값 풀 대기 30,018ms).
     * 애플리케이션 버그(500)와 인프라 포화(503)를 분리해 판정 가능하게 하고, 커밋 이전에
     * 트랜잭션 자체가 시작되지 않으므로 주문 등 쓰기는 발생하지 않는다. Retry-After 는
     * [LoadSheddingFilter][com.sportsapp.infrastructure.loadshedding.LoadSheddingFilter] 와
     * 동일한 503 계약(code=SERVICE_UNAVAILABLE, Retry-After=1s)을 따른다.
     */
    @ExceptionHandler(CannotCreateTransactionException::class)
    fun handleCannotCreateTransactionException(
        exception: CannotCreateTransactionException,
    ): ResponseEntity<ProblemDetail> {
        logger.warn("Connection pool exhausted while starting transaction: {}", exception.message)
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.SERVICE_UNAVAILABLE,
            code = "SERVICE_UNAVAILABLE",
            detail = "Server is under heavy load. Please retry shortly."
        )
        return ResponseEntity.status(ErrorStatus.SERVICE_UNAVAILABLE.httpStatus)
            .header("Retry-After", "1")
            .body(problemDetail)
    }

    /**
     * [FIX-03][code-review p3] `@Transactional` 시작 시점(트랜잭션 밖)이 아니라, 트랜잭션이
     * 이미 열린 상태에서 별도로 커넥션을 얻는 경로(독립 `@Repository` + QueryDSL 구현, 호출부
     * UseCase가 `@Transactional` 없이 조회만 수행하는 경우 등)의 풀 고갈은
     * [CannotCreateTransactionException]이 아니라 [DataAccessResourceFailureException]
     * (그 하위 타입 [org.springframework.jdbc.CannotGetJdbcConnectionException] 포함)으로
     * 표면화된다. 이 경로를 매핑하지 않으면 catch-all(500)로 떨어져 풀 고갈이 애플리케이션
     * 버그로 오분류된다 — [LimitedDropConnectionPoolExhaustionScenarioTest]의 점유 상태 조회가
     * 이 예외로 실패함을 근거로 명시한다.
     */
    @ExceptionHandler(DataAccessResourceFailureException::class)
    fun handleDataAccessResourceFailureException(
        exception: DataAccessResourceFailureException,
    ): ResponseEntity<ProblemDetail> {
        logger.warn("Connection pool exhausted while accessing data: {}", exception.message)
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.SERVICE_UNAVAILABLE,
            code = "SERVICE_UNAVAILABLE",
            detail = "Server is under heavy load. Please retry shortly."
        )
        return ResponseEntity.status(ErrorStatus.SERVICE_UNAVAILABLE.httpStatus)
            .header("Retry-After", "1")
            .body(problemDetail)
    }

    /**
     * [FIX-03][code-review p3] 커넥션을 획득한 뒤 쿼리 실행 단계에서 풀 포화·DB 부하로
     * 타임아웃되는 경로 — 위 [DataAccessResourceFailureException]과 마찬가지로 트랜잭션 밖
     * catch-all(500)로 떨어지지 않도록 503(풀/DB 포화)로 분류한다.
     */
    @ExceptionHandler(QueryTimeoutException::class)
    fun handleQueryTimeoutException(exception: QueryTimeoutException): ResponseEntity<ProblemDetail> {
        logger.warn("Query timed out under load: {}", exception.message)
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.SERVICE_UNAVAILABLE,
            code = "SERVICE_UNAVAILABLE",
            detail = "Server is under heavy load. Please retry shortly."
        )
        return ResponseEntity.status(ErrorStatus.SERVICE_UNAVAILABLE.httpStatus)
            .header("Retry-After", "1")
            .body(problemDetail)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnknownException(exception: Exception): ResponseEntity<ProblemDetail> {
        logger.error("Unhandled exception", exception)
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.INTERNAL,
            code = "INTERNAL_ERROR",
            detail = "An unexpected error occurred"
        )
        return ResponseEntity.status(ErrorStatus.INTERNAL.httpStatus).body(problemDetail)
    }
}
