package com.sportsapp.domain.identity.vo

/**
 * edge 가 검증을 끝낸 신원 — 내부 헤더로 하위 서비스에 전파되는 값 (W1-06b §6-3).
 *
 * 하위 서비스는 이 값만 신뢰한다. 따라서 **외부에서 들어온 동일 헤더는 반드시 폐기**되어야 하며,
 * 그 방어는 `InternalIdentityHeaderRequest` 가 담당한다 (nginx 제거에 의존하지 않는 2중 방어).
 */
data class InternalIdentity(
    val subjectId: Long,
    val channel: InternalAuthChannel,
    val scopes: List<String>,
) {
    /** 헤더 값은 단일 문자열이어야 하므로 스코프 목록을 콤마로 잇는다. 스코프가 없으면 빈 문자열. */
    fun scopesAsHeaderValue(): String = scopes.joinToString(",")
}
