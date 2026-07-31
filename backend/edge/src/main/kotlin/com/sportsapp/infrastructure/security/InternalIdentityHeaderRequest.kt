package com.sportsapp.infrastructure.security

import com.sportsapp.domain.identity.vo.InternalIdentity
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.util.Collections
import java.util.Enumeration

/**
 * 내부 신원 헤더를 **덮어쓰는** 요청 래퍼 (W1-06b §6-3 스푸핑 방어 ②).
 *
 * 원본 요청의 [InternalIdentityHeaders] 목록은 출처와 무관하게 **전부 폐기**하고, [identity] 가
 * 있을 때만 그 검증 결과로 값을 채운다. 즉 외부에서 위조해 보낸 헤더는 어떤 경우에도 하위로 넘어가지
 * 않는다 — nginx 제거(방어 ①)를 우회하는 경로가 있어도 여기서 막힌다.
 *
 * [identity] 가 null 이면 "폐기 전용" 래퍼가 된다 (미인증 요청·인증 대상이 아닌 요청).
 */
class InternalIdentityHeaderRequest(
    request: HttpServletRequest,
    private val identity: InternalIdentity?,
) : HttpServletRequestWrapper(request) {

    private val injectedHeaders: Map<String, String> = identity?.let {
        mapOf(
            InternalIdentityHeaders.SUBJECT to it.subjectId.toString(),
            InternalIdentityHeaders.CHANNEL to it.channel.name,
            InternalIdentityHeaders.SCOPES to it.scopesAsHeaderValue(),
        )
    } ?: emptyMap()

    override fun getHeader(name: String): String? {
        if (!InternalIdentityHeaders.isInternal(name)) return super.getHeader(name)
        return injectedHeaders.entries
            .firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
    }

    override fun getHeaders(name: String): Enumeration<String> {
        if (!InternalIdentityHeaders.isInternal(name)) return super.getHeaders(name)
        return Collections.enumeration(listOfNotNull(getHeader(name)))
    }

    override fun getHeaderNames(): Enumeration<String> {
        val externalNames = super.getHeaderNames().toList().filterNot { InternalIdentityHeaders.isInternal(it) }
        return Collections.enumeration(externalNames + injectedHeaders.keys)
    }
}
