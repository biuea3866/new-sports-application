package com.sportsapp.infrastructure.security

/**
 * 내부 신원 전파 헤더 계약 (W1-06b §6-3).
 *
 * edge 가 검증한 신원을 하위 서비스로 넘기는 유일한 수단이며, **하위 서비스는 이 헤더만 신뢰한다.**
 * 그래서 이 이름들은 외부에서 들어올 수 없어야 한다 — 2중 방어:
 *   1. nginx 가 외부 요청의 이 헤더들을 빈 값으로 덮는다 (`infra/nginx/lb.conf`)
 *   2. `InternalIdentityHeaderSanitizingFilter` 가 외부 유입분을 무조건 폐기하고, 인증 필터가
 *      자기 검증 결과로만 채운다
 * 1번만 믿지 않는다 — nginx 를 우회하는 경로(직접 포트 노출·다른 인그레스)가 존재할 수 있다.
 *
 * `X-Internal-` 접두사를 고정한다 — nginx 설정과 이 상수가 같은 접두사를 공유해야 방어 목록이 갈리지 않는다.
 */
object InternalIdentityHeaders {

    const val SUBJECT = "X-Internal-Auth-Subject"
    const val CHANNEL = "X-Internal-Auth-Channel"
    const val SCOPES = "X-Internal-Auth-Scopes"

    /** 방어 대상 전체 목록. 헤더를 추가하면 이 집합에도 반드시 넣어야 폐기 대상이 된다. */
    val ALL: Set<String> = setOf(SUBJECT, CHANNEL, SCOPES)

    /** 헤더 이름은 대소문자를 구분하지 않는다 — 위조 시도가 소문자로 오는 경우까지 폐기한다. */
    fun isInternal(headerName: String): Boolean = ALL.any { it.equals(headerName, ignoreCase = true) }
}
