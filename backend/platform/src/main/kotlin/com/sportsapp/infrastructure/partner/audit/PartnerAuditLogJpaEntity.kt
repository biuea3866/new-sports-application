package com.sportsapp.infrastructure.partner.audit

import com.sportsapp.domain.partner.audit.PartnerAuditLog
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.ZonedDateTime

/**
 * `partner_audit_log` 테이블 JPA 매핑. append-only(생성만, 수정·삭제 없음).
 * 순수 POJO 도메인 [PartnerAuditLog]와는 [of]/[toDomain]으로 상호 변환한다.
 */
@Entity
@Table(name = "partner_audit_log")
@EntityListeners(AuditingEntityListener::class)
class PartnerAuditLogJpaEntity private constructor(
    @Column(name = "partner_id", nullable = false)
    val partnerId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "http_method", nullable = false, length = 10)
    val httpMethod: String,

    @Column(name = "request_path", nullable = false, length = 512)
    val requestPath: String,

    @Column(name = "target_resource", length = 255)
    val targetResource: String?,

    @Column(name = "status_code", nullable = false)
    val statusCode: Int,

    @Column(name = "latency_ms", nullable = false)
    val latencyMs: Int,

    @Column(name = "ip_addr", length = 45)
    val ipAddr: String?,

    @Column(name = "client_user_agent", length = 500)
    val clientUserAgent: String?,

    @Column(name = "called_at", nullable = false)
    val calledAt: ZonedDateTime,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        protected set

    fun toDomain(): PartnerAuditLog = PartnerAuditLog.reconstitute(
        id = requireNotNull(id) { "PartnerAuditLogJpaEntity.id must not be null after persist" },
        partnerId = partnerId,
        userId = userId,
        httpMethod = httpMethod,
        requestPath = requestPath,
        targetResource = targetResource,
        statusCode = statusCode,
        latencyMs = latencyMs,
        ipAddr = ipAddr,
        clientUserAgent = clientUserAgent,
        calledAt = calledAt,
    )

    companion object {
        fun of(auditLog: PartnerAuditLog): PartnerAuditLogJpaEntity = PartnerAuditLogJpaEntity(
            partnerId = auditLog.partnerId,
            userId = auditLog.userId,
            httpMethod = auditLog.httpMethod,
            requestPath = auditLog.requestPath,
            targetResource = auditLog.targetResource,
            statusCode = auditLog.statusCode,
            latencyMs = auditLog.latencyMs,
            ipAddr = auditLog.ipAddr,
            clientUserAgent = auditLog.clientUserAgent,
            calledAt = auditLog.calledAt,
        )
    }
}
