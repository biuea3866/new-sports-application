package com.sportsapp.domain.common

abstract class BusinessException(
    val errorCode: String,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    abstract val status: ErrorStatus
}

enum class ErrorStatus(val httpStatus: Int) {
    NOT_FOUND(404),
    CONFLICT(409),
    UNPROCESSABLE(422),
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    TOO_MANY_REQUESTS(429),
    TOO_EARLY(425),
    INTERNAL(500),
    SERVICE_UNAVAILABLE(503),
}
