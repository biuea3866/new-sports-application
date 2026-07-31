package com.sportsapp.domain.user.service

import com.sportsapp.domain.user.repository.PermissionRepository
import org.springframework.stereotype.Service

/**
 * 권한(permission) 마스터 조회 공개 계약.
 *
 * PH0-05: domain/common 이 소유하던 Permission 을 user 로 이관하며 신설한 공개 조회 계약.
 * mcp 등 다른 컨텍스트는 이 클래스를 직접 의존하지 않고 자기 소유 ACL Gateway(예: McpPermissionGateway)를
 * 경유해서만 조회한다.
 */
@Service
class PermissionDomainService(
    private val permissionRepository: PermissionRepository,
) {
    fun findIdByName(name: String): Long? =
        permissionRepository.findByName(name)?.id

    fun findNamesByIds(ids: List<Long>): Map<Long, String> =
        permissionRepository.findAllByIds(ids).associate { permission -> permission.id to permission.name }
}
