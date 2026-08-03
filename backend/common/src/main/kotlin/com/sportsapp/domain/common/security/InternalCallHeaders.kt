package com.sportsapp.domain.common.security

/**
 * 서비스 간 호출을 증명하는 헤더 이름 계약 (S2-02).
 *
 * 발신은 edge(S2-08), 검증은 모놀리스(S2-07)가 각자 구현한다 — 두 모듈이 같은 이름을 알아야
 * 하고, 그 유일한 공통 지점이 `common`이다. 값·필터·인가 규칙은 이 계약의 범위가 아니다
 * (이름만 고정한다).
 *
 * 신원 전파 헤더(`X-Internal-Auth-Subject` 등)는 edge 소유 `InternalIdentityHeaders`에 이미
 * 있고 여기로 옮기지 않는다 — 이 헤더의 공급자 모듈(모놀리스·edge)은 edge 를 의존하지 않아
 * 참조할 수 없으므로, 리터럴 일치를 `InternalIngressGuardTest` 확장(S2-07)이 강제한다.
 */
object InternalCallHeaders {

    /** 호출자 인증용 공유 시크릿 헤더 — 값 검증은 S2-07, 발신은 S2-08 이 담당한다. */
    const val CALL_TOKEN = "X-Internal-Call-Token"
}
