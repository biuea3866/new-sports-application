package com.sportsapp.presentation.exception

import com.sportsapp.domain.common.ErrorStatus
import org.springframework.http.ProblemDetail
import java.net.URI

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
