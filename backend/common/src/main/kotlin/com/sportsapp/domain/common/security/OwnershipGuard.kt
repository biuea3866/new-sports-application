package com.sportsapp.domain.common.security

/**
 * 리소스 단위 ownership 검증 컴포넌트.
 *
 * @PreAuthorize 는 Role/Permission 레벨을 검사하고, 이 컴포넌트는 "내 리소스인가" 를 검사합니다.
 * UseCase 진입 첫 줄에서 호출해 타 사업자 리소스 접근 시 404 를 반환합니다 (존재 자체 비노출).
 *
 * 도메인 layer 에는 interface 만 두고 SecurityContext 의존 구현체는 infrastructure 에서 제공합니다.
 */
interface OwnershipGuard {

    /**
     * ownerUserId 와 authUserId 가 일치하지 않으면 ResourceNotFoundException(404) 을 던집니다.
     *
     * ownerUserId 가 null 인 경우는 통과시킵니다.
     * 이유: admin 시드 데이터 또는 B2C 호환 목적으로 생성된 리소스는 ownerUserId 가 없을 수 있으며,
     *       B2C 사용자도 해당 리소스에 접근할 수 있어야 합니다.
     */
    fun requireOwned(ownerUserId: Long?, authUserId: Long)

    /**
     * SecurityContext 에서 인증된 사용자 id 를 추출합니다.
     * 미인증 상태이면 UnauthorizedException(401) 을 던집니다.
     */
    fun authUserId(): Long
}
