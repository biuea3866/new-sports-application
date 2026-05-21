package com.sportsapp.domain.mcp

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.ZonedDateTime

@Entity
@Table(name = "mcp_audit_logs")
@EntityListeners(AuditingEntityListener::class)
class McpAuditLog(
    @Column(name = "token_id")
    val tokenId: Long?,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "tool_name", nullable = false, length = 100)
    val toolName: String,

    @Column(name = "params_masked", columnDefinition = "TEXT")
    val paramsMasked: String?,

    @Column(name = "status_code", nullable = false)
    val statusCode: Int,

    @Column(name = "latency_ms", nullable = false)
    val latencyMs: Int,

    @Column(name = "client_user_agent", length = 500)
    val clientUserAgent: String?,

    @Column(name = "ip_addr", length = 45)
    val ipAddr: String?,

    @Column(name = "asn", length = 100)
    val asn: String?,

    @Column(name = "called_at", nullable = false)
    val calledAt: ZonedDateTime,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        protected set

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    var createdBy: Long? = null
        protected set
}
