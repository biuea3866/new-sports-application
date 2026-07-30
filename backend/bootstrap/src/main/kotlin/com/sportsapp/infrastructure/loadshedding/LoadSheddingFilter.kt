package com.sportsapp.infrastructure.loadshedding

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.Semaphore

/**
 * [F2] 실측 부하 테스트 결과 — 동시 인플라이트 요청이 CPU 처리 한계를 넘으면 서블릿 컨테이너
 * 큐(Tomcat accept queue)에서 최대 60초까지 대기하다, 한 번 무너지면 재기동 전까지 영구
 * 열화한다. 허용 동시 처리 수([maxConcurrentRequests])를 초과하는 요청은 큐잉하지 않고
 * 즉시 503으로 거부한다(fast-fail).
 *
 * 인증 연산(JWT 파싱 등) 이전에 거부하도록 [SecurityConfig][com.sportsapp.infrastructure.security.SecurityConfig]에서
 * 필터 체인 최전방에 등록한다. 헬스체크 경로는 셰딩 대상에서 제외해 과부하 시에도
 * 오케스트레이터의 헬스체크 자체가 실패하지 않게 한다. [enabled]로 기능 전체를 즉시 끌 수 있다.
 */
@Component
class LoadSheddingFilter(
    @Value("\${load-shedding.max-concurrent-requests:200}") private val maxConcurrentRequests: Int,
    @Value("\${load-shedding.enabled:true}") private val enabled: Boolean,
) : OncePerRequestFilter() {

    private val permits = Semaphore(maxConcurrentRequests, false)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (!enabled) return true
        val path = request.requestURI
        return path.startsWith("/actuator/") || path == "/healthz"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!permits.tryAcquire()) {
            writeServiceUnavailable(response)
            return
        }
        try {
            filterChain.doFilter(request, response)
        } finally {
            permits.release()
        }
    }

    private fun writeServiceUnavailable(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_SERVICE_UNAVAILABLE
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.setHeader("Retry-After", RETRY_AFTER_SECONDS)
        response.writer.write(SERVICE_UNAVAILABLE_BODY)
    }

    companion object {
        private const val RETRY_AFTER_SECONDS = "1"
        private const val SERVICE_UNAVAILABLE_BODY =
            """{"status":503,"title":"Service Unavailable",""" +
                """"detail":"Server is under heavy load. Please retry shortly.",""" +
                """"properties":{"code":"SERVICE_UNAVAILABLE"}}"""
    }
}
