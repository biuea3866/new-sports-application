package com.sportsapp.domain.mcp.gateway

/**
 * mcp 관점의 권한 조회 ACL(Anti-Corruption Layer).
 *
 * PH0-05: Permission 이 domain/common 에서 domain/user 로 이관되며, mcp(subsystem)가 user(core)를
 * 동기 의존(import)하지 않도록 이 계약 뒤로 조회를 숨긴다. 구현체는 infrastructure 레이어에서
 * PermissionDomainService(user)를 주입해 위임한다.
 */
interface McpPermissionGateway {
    fun findPermissionIdBy(permissionName: String): Long?

    /**
     * W1-06a: MCP 토큰 검증 시 저장된 permissionId 목록을 permission name으로 역해석하기 위한 조회.
     * `findPermissionIdBy`(이름→id)의 반대 방향(id들→이름)이다.
     */
    fun findPermissionNamesBy(permissionIds: List<Long>): Map<Long, String>
}
