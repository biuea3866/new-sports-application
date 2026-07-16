package com.sportsapp.presentation.virtualqueue.interceptor

import com.sportsapp.domain.common.EntryTokenGuard
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.domain.virtualqueue.VirtualQueueFeatureFlagKeys
import com.sportsapp.domain.virtualqueue.exception.QueueBypassDeniedException
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 구매 앞단 입장 토큰 검증 게이트 (FR-5, BE-09, TDD "API 계약" 5번).
 *
 * `com.sportsapp.infrastructure.virtualqueue.config.VirtualQueueWebMvcConfig`가 이 인터셉터를
 * [LIMITED_DROP_ORDER_PATH]·[TICKETING_SELECT_SEATS_PATH] 두 경로에만 등록한다 — 등록 밖의
 * 경로는 Spring이 [preHandle] 자체를 호출하지 않으므로 게이트 대상이 아니다.
 *
 * `EntryTokenGuard`·`FeatureFlagEvaluator`(둘 다 domain.common)만 의존한다 — goods/ticketing
 * 도메인 패키지를 참조하지 않는다(TDD "시스템 역할 경계", 교차 도메인 결합 금지).
 *
 * 플래그(`virtualqueue.enabled`) OFF면 검증을 스킵해 기존 직접 구매 경로를 그대로 허용한다
 * (FR-9). ON이면 `X-Entry-Token` 헤더를 [EntryTokenGuard.verify]로 검증하고, 실패(부재·위조·
 * 만료·인증 principal 부재) 시 [QueueBypassDeniedException](403)을 던지며
 * [BYPASS_ATTEMPT_COUNTER]를 증가시킨다.
 *
 * AUTH-04 — userId는 더 이상 클라이언트가 임의로 조작 가능한 커스텀 사용자 식별 헤더가 아니라
 * `SecurityContextHolder`에 담긴 JWT 인증 principal에서 읽는다. `JwtAuthenticationFilter`가
 * 서블릿 필터 체인에서 이 인터셉터([preHandle])보다 먼저 실행되므로, 이 경로들이 이미
 * SecurityConfig 상 `authenticated()`인 한 principal은 항상 채워져 있다.
 */
@Component
class EntryTokenGateInterceptor(
    private val entryTokenGuard: EntryTokenGuard,
    private val featureFlagEvaluator: FeatureFlagEvaluator,
    private val meterRegistry: MeterRegistry,
) : HandlerInterceptor {

    private val pathMatcher = AntPathMatcher()

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val target = resolveTarget(request.requestURI) ?: return true
        val userId = resolveAuthenticatedUserId()
        val shouldPass = isFlagDisabled(userId) || isVerified(target, userId, request.getHeader(ENTRY_TOKEN_HEADER))
        if (shouldPass) return true

        meterRegistry.counter(BYPASS_ATTEMPT_COUNTER).increment()
        throw QueueBypassDeniedException(target)
    }

    private fun resolveAuthenticatedUserId(): Long? =
        (SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal)?.id

    private fun isFlagDisabled(userId: Long?): Boolean =
        !featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ENABLED, FeatureContext.of(userId), false)

    private fun isVerified(target: QueueTarget, userId: Long?, rawToken: String?): Boolean =
        userId != null && entryTokenGuard.verify(target.type.slug, target.targetId, userId, rawToken)

    private fun resolveTarget(requestUri: String): QueueTarget? {
        val limitedDropId = extractTemplateVariable(LIMITED_DROP_ORDER_PATH, requestUri, DROP_ID_VARIABLE)
        if (limitedDropId != null) return QueueTarget(QueueTargetType.LIMITED_DROP, limitedDropId)

        val ticketingEventId = extractTemplateVariable(TICKETING_SELECT_SEATS_PATH, requestUri, EVENT_ID_VARIABLE)
        return ticketingEventId?.let { QueueTarget(QueueTargetType.TICKETING_EVENT, it) }
    }

    private fun extractTemplateVariable(pattern: String, requestUri: String, variableName: String): Long? {
        if (!pathMatcher.match(pattern, requestUri)) return null
        return pathMatcher.extractUriTemplateVariables(pattern, requestUri)[variableName]?.toLongOrNull()
    }

    companion object {
        /** [com.sportsapp.presentation.goods.controller.LimitedDropApiController.purchase]와 동일 경로. */
        const val LIMITED_DROP_ORDER_PATH = "/limited-drops/{dropId}/orders"

        /** [com.sportsapp.presentation.ticketing.controller.EventApiController.selectSeats]와 동일 경로. */
        const val TICKETING_SELECT_SEATS_PATH = "/events/{eventId}/seats/select"

        private const val DROP_ID_VARIABLE = "dropId"
        private const val EVENT_ID_VARIABLE = "eventId"
        private const val ENTRY_TOKEN_HEADER = "X-Entry-Token"
        private const val BYPASS_ATTEMPT_COUNTER = "virtual_queue.bypass_attempt"
    }
}
