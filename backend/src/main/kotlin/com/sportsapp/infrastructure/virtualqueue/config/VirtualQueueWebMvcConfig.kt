package com.sportsapp.infrastructure.virtualqueue.config

import com.sportsapp.presentation.virtualqueue.interceptor.EntryTokenGateInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * [EntryTokenGateInterceptor]를 구매 앞단 두 경로(한정판 구매·티케팅 좌석선택)에만 등록한다
 * (BE-09, TDD "시스템 역할 경계"). 등록 대상 외 경로(예: `GET /events`)는 이 인터셉터의
 * `preHandle`을 아예 거치지 않는다. `SecurityConfig`·기존 컨트롤러는 이 티켓에서 수정하지
 * 않는다(Single Writer per File — BE-08 소유).
 */
@Configuration
class VirtualQueueWebMvcConfig(
    private val entryTokenGateInterceptor: EntryTokenGateInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(entryTokenGateInterceptor)
            .addPathPatterns(
                EntryTokenGateInterceptor.LIMITED_DROP_ORDER_PATH,
                EntryTokenGateInterceptor.TICKETING_SELECT_SEATS_PATH,
            )
    }
}
