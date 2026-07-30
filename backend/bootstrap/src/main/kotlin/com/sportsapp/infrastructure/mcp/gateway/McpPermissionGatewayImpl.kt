package com.sportsapp.infrastructure.mcp.gateway

import com.sportsapp.domain.mcp.gateway.McpPermissionGateway
import com.sportsapp.domain.user.service.PermissionDomainService
import org.springframework.stereotype.Component

/**
 * McpPermissionGateway 구현체.
 *
 * user 도메인의 공급자 PermissionDomainService 를 주입해 위임만 수행한다 (ACL 어댑터).
 * infrastructure 는 R3(서브시스템→코어 동기 의존 금지) 스캔 대상이 아니므로 이 클래스가
 * user 를 직접 참조해도 규칙 위반이 아니다.
 */
@Component
class McpPermissionGatewayImpl(
    private val permissionDomainService: PermissionDomainService,
) : McpPermissionGateway {

    override fun findPermissionIdBy(permissionName: String): Long? =
        permissionDomainService.findIdByName(permissionName)
}
