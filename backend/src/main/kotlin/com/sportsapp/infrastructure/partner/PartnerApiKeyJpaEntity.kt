package com.sportsapp.infrastructure.partner

import com.sportsapp.domain.partner.entity.ApiKeyStatus
import com.sportsapp.domain.partner.entity.PartnerApiKey
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener

/**
 * `partner_api_key` 테이블 JPA 매핑. 순수 POJO 도메인 [PartnerApiKey]와는 [of]/[toDomain]으로 상호 변환한다.
 */
@Entity
@Table(name = "partner_api_key")
@EntityListeners(AuditingEntityListener::class)
class PartnerApiKeyJpaEntity private constructor(
    @Column(name = "partner_id", nullable = false)
    val partnerId: Long,

    @Column(name = "key_hash", nullable = false, length = 255)
    val keyHash: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: ApiKeyStatus,

    @Column(name = "revoked_at")
    var revokedAt: ZonedDateTime?,

    @Column(name = "last_used_at")
    var lastUsedAt: ZonedDateTime?,
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

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    var createdBy: Long? = null
        protected set

    /** 기존 row 갱신 시 도메인의 가변 상태를 반영한다. */
    fun applyFrom(apiKey: PartnerApiKey) {
        this.status = apiKey.status
        this.revokedAt = apiKey.revokedAt
        this.lastUsedAt = apiKey.lastUsedAt
    }

    fun toDomain(): PartnerApiKey = PartnerApiKey.reconstitute(
        id = requireNotNull(id) { "PartnerApiKeyJpaEntity.id must not be null after persist" },
        partnerId = partnerId,
        keyHash = keyHash,
        status = status,
        revokedAt = revokedAt,
        lastUsedAt = lastUsedAt,
    )

    companion object {
        fun of(apiKey: PartnerApiKey): PartnerApiKeyJpaEntity = PartnerApiKeyJpaEntity(
            partnerId = apiKey.partnerId,
            keyHash = apiKey.keyHash,
            status = apiKey.status,
            revokedAt = apiKey.revokedAt,
            lastUsedAt = apiKey.lastUsedAt,
        )
    }
}
