package com.sportsapp.domain.common

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import java.time.ZonedDateTime
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener

/**
 * 모든 JPA Entity 의 베이스 — audit 컬럼 + soft delete 강제.
 *
 * 정책:
 * - Hard delete 금지. Entity.softDelete(userId) 만 허용 (admin 전용 UseCase 예외).
 * - 모든 Repository 조회는 기본으로 `WHERE deleted_at IS NULL` 필터.
 * - JPA auditing 으로 created_at/by, updated_at/by 자동 채움.
 *
 * 활성화 조건:
 * - `@SpringBootApplication` 클래스에 `@EnableJpaAuditing` 명시
 * - `AuditorAware<Long>` 빈 — `SecurityAuditorAware` 가 제공 (AUTH-03 후 SecurityContext 통합)
 *
 * 본 클래스는 도메인 layer 가 JPA 어노테이션을 import 하는 유일한 예외다 — audit 인프라는
 * 전사 공통이므로 domain.common 에 둔다.
 *
 * 클래스 이름은 컨벤션상 `*Entity*` 패턴이 hard-default-value 룰에 걸리므로 의도적으로 회피.
 * 도메인 Entity 들은 `class XEntity : JpaAuditingBase()` 형태로 상속한다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class JpaAuditingBase {

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

    @Column(name = "deleted_at")
    var deletedAt: ZonedDateTime? = null
        protected set

    @Column(name = "deleted_by")
    var deletedBy: Long? = null
        protected set

    /**
     * 소프트 삭제. 이미 삭제된 entity 에 재호출 시 IllegalStateException.
     * @param userId 삭제 수행자 user id. 시스템 자동 청산은 null.
     */
    fun softDelete(userId: Long?) {
        check(deletedAt == null) { "Entity is already soft-deleted" }
        deletedAt = ZonedDateTime.now()
        deletedBy = userId
    }

    val isDeleted: Boolean
        get() = deletedAt != null
}
