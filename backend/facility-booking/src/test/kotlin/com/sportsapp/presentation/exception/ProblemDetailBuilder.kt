package com.sportsapp.presentation.exception

import com.sportsapp.domain.common.ErrorStatus
import org.springframework.http.ProblemDetail
import java.net.URI

/**
 * [W1-01b] facility-booking 소유 standalone MockMvc 컨트롤러 테스트(Booking·Facility ApiControllerTest) 전용 테스트
 * 하네스 로컬 복제본. bootstrap 의 `com.sportsapp.presentation.exception.ProblemDetailBuilder` 와
 * 동일 계약 — GlobalExceptionHandler.kt KDoc 참고.
 */
object ProblemDetailBuilder {

    fun build(status: ErrorStatus, code: String, detail: String): ProblemDetail {
        val problemDetail = ProblemDetail.forStatus(status.httpStatus)
        problemDetail.type = URI.create("https://errors.sports-application/${code.lowercase().replace('_', '-')}")
        problemDetail.title = code.split('_').joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
        problemDetail.detail = detail
        problemDetail.setProperty("code", code)
        return problemDetail
    }
}
