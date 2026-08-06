package com.sportsapp.edgeapp.upstream

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * edge 가 호출할 상류 서비스 주소와 호출자 인증 시크릿 (S2-08 ⑤).
 *
 * **기본값이 전부 모놀리스인 것이 핵심이다.** 2단계에는 세 공급자가 모두 모놀리스 안에 있고,
 * 이후 commerce·facility-booking·social 이 각자 추출될 때 **해당 한 줄만** 신규 컨테이너 호스트로
 * 바꾸면 그 경로만 넘어간다. `PLATFORM_INTERNAL_BASE_URL`(W1-02 선배선)과 같은 패턴이다.
 *
 * [callToken] 은 빈 값이 기본이다 — 주입 전에는 헤더를 붙이지 않는다. 빈 값 헤더를 보내면
 * 모놀리스의 호출자 인증(S2-07)이 그것을 "제시된 토큰"으로 받아 대조 실패 로그만 늘린다.
 */
@ConfigurationProperties(prefix = "edge.upstream")
data class EdgeUpstreamProperties(
    val commerce: Upstream = Upstream(),
    val facilityBooking: Upstream = Upstream(),
    val social: Upstream = Upstream(),
    val callToken: String = "",
) {
    data class Upstream(val baseUrl: String = MONOLITH_BASE_URL)

    companion object {
        /** 2단계 기본 상류 — 세 공급자가 아직 모놀리스 안에 있다. */
        const val MONOLITH_BASE_URL = "http://backend:8080"
    }
}
