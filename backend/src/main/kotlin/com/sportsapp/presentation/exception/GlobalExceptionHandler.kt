package com.sportsapp.presentation.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.goods.exception.LimitedDropTooEarlyException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorizationDeniedException
import jakarta.validation.ConstraintViolationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

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

    /**
     * [LimitedDropTooEarlyException]은 [BusinessException]보다 먼저 매칭돼(더 구체적인 타입)
     * 응답 본문에 [LimitedDropTooEarlyException.openAt]을 추가로 포함한다 — FE 재시도 시점 판단용.
     */
    @ExceptionHandler(LimitedDropTooEarlyException::class)
    fun handleLimitedDropTooEarlyException(exception: LimitedDropTooEarlyException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = exception.status,
            code = exception.errorCode,
            detail = exception.message
        )
        problemDetail.setProperty("openAt", exception.openAt.toString())
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

    /**
     * 매핑된 컨트롤러도, 정적 리소스도 없는 요청(예: 피처 플래그로 컨트롤러 빈이 제거된 경로)에
     * Spring 6.1+ [ResourceHttpRequestHandler]가 던지는 예외. 하위 [Exception] 핸들러가 catch-all이라
     * 별도 처리하지 않으면 500으로 변환되므로, 404로 명시 매핑한다.
     */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(exception: NoResourceFoundException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.NOT_FOUND,
            code = "NOT_FOUND",
            detail = "Requested resource does not exist"
        )
        return ResponseEntity.status(ErrorStatus.NOT_FOUND.httpStatus).body(problemDetail)
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
