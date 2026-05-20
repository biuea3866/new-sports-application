package com.sportsapp.infrastructure.security

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.user.UserPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * 리소스 단위 ownership 검증 컴포넌트.
 *
 * @PreAuthorize 는 Role/Permission 레벨 검사를 담당하고,
 * 이 컴포넌트는 "내 리소스인가" 를 검사한다.
 * UseCase 진입 첫 줄에서 호출해 타 사업자 리소스 접근 시 404 를 반환한다 (존재 자체 비노출).
 */
@Component
class OwnershipGuard {

    /**
     * ownerUserId 와 authUserId 가 일치하지 않으면 ResourceNotFoundException(404) 을 던진다.
     *
     * ownerUserId 가 null 인 경우는 통과시킨다.
     * 이유: admin 시드 데이터 또는 B2C 호환 목적으로 생성된 리소스는 ownerUserId 가 없을 수 있으며,
     *       B2C 사용자도 해당 리소스에 접근할 수 있어야 한다.
     */
    fun requireOwned(ownerUserId: Long?, authUserId: Long) {
        if (ownerUserId != null && ownerUserId != authUserId) {
            throw ResourceNotFoundException("Resource", authUserId)
        }
    }

    /**
     * SecurityContext 에서 인증된 사용자 id 를 추출한다.
     * 미인증 상태이면 SecurityException 을 던진다.
     */
    fun authUserId(): Long {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal
            ?: throw SecurityException("Authenticated user not found in SecurityContext")
        return principal.id
    }
}
