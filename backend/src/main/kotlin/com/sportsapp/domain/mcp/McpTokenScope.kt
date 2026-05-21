package com.sportsapp.domain.mcp

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * MCP 토큰-Permission 매핑 Entity (1급 Entity).
 * 매핑 테이블 독립 Entity 원칙에 따라 자체 PK + audit 6 컬럼 보유.
 * token_id + permission_id FK만 보유하며, 연관 객체 직접 참조 금지.
 */
@Entity
@Table(name = "mcp_token_scopes")
class McpTokenScope(
    @Column(name = "token_id", nullable = false)
    val tokenId: Long,

    @Column(name = "permission_id", nullable = false)
    val permissionId: Long,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    companion object {
        fun create(tokenId: Long, permissionId: Long): McpTokenScope =
            McpTokenScope(tokenId = tokenId, permissionId = permissionId)
    }
}
