package com.sportsapp.infrastructure.partner

import com.sportsapp.domain.partner.entity.Partner
import com.sportsapp.domain.partner.entity.PartnerStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.ZonedDateTime
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener

/**
 * `partner` 테이블 JPA 매핑. 순수 POJO 도메인 [Partner]와는 [of]/[toDomain]으로 상호 변환한다.
 */
@Entity
@Table(name = "partner")
@EntityListeners(AuditingEntityListener::class)
class PartnerJpaEntity private constructor(
    @Column(name = "name", nullable = false, length = 255)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: PartnerStatus,

    @Column(name = "linked_user_id", nullable = false)
    val linkedUserId: Long,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        protected set

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        protected set

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    var createdBy: Long? = null
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: ZonedDateTime
        protected set

    @LastModifiedBy
    @Column(name = "updated_by")
    var updatedBy: Long? = null
        protected set

    /** 기존 row 갱신 시 도메인의 가변 상태를 반영한다. */
    fun applyFrom(partner: Partner) {
        this.status = partner.status
    }

    fun toDomain(): Partner = Partner.reconstitute(
        id = requireNotNull(id) { "PartnerJpaEntity.id must not be null after persist" },
        name = name,
        status = status,
        linkedUserId = linkedUserId,
    )

    companion object {
        fun of(partner: Partner): PartnerJpaEntity = PartnerJpaEntity(
            name = partner.name,
            status = partner.status,
            linkedUserId = partner.linkedUserId,
        )
    }
}
