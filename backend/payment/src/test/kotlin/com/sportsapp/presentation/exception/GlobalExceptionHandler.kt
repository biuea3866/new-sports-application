package com.sportsapp.presentation.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import org.slf4j.LoggerFactory
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
 * [W1-01b] payment 소유 standalone MockMvc 컨트롤러 테스트(PaymentApiControllerTest) 전용 테스트
 * 하네스 로컬 복제본. bootstrap 의 `com.sportsapp.presentation.exception.GlobalExceptionHandler` 와
 * 매핑 로직이 동일하다 — 배경·제약은 commerce 의 동명 파일 KDoc 참고.
 *
 * goods 전용 핸들러([LimitedDropTooEarlyException])는 payment 모듈에 goods 도메인이 없어 제외한다
 * (payment 자신의 컨트롤러 테스트는 이 예외를 던지지 않는다 — BusinessException 상위 핸들러로 폴백
 * 되는 원본과 동작 차이는 없다).
 */
@RestControllerAdvice
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

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(exception: NoResourceFoundException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.NOT_FOUND,
            code = "NOT_FOUND",
            detail = "Requested resource does not exist"
        )
        return ResponseEntity.status(ErrorStatus.NOT_FOUND.httpStatus).body(problemDetail)
    }

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
