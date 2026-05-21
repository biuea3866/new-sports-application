package com.sportsapp.presentation.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

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

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(exception: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.UNPROCESSABLE,
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
        return ResponseEntity.status(ErrorStatus.UNPROCESSABLE.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(exception: AccessDeniedException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN)
        problemDetail.detail = exception.message ?: "Access denied"
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail)
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
