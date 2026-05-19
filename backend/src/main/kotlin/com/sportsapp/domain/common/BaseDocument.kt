package com.sportsapp.domain.common

import java.time.ZonedDateTime
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate

/**
 * 모든 MongoDB Document 의 베이스 — audit 필드 + soft delete 강제.
 *
 * 활성화 조건:
 * - MongoConfig 에 `@EnableMongoAuditing` 명시
 * - `AuditorAware<Long>` 빈 — SecurityContext 에서 user id 추출 (JPA 와 동일 빈 재사용 가능)
 *
 * JPA `BaseEntity` 와 달리 컬럼 어노테이션이 없으므로 Document 클래스가 자기 컬렉션 이름만 선언하면 된다.
 */
abstract class BaseDocument {

    @CreatedDate
    lateinit var createdAt: ZonedDateTime
        protected set

    @CreatedBy
    var createdBy: Long? = null
        protected set

    @LastModifiedDate
    lateinit var updatedAt: ZonedDateTime
        protected set

    @LastModifiedBy
    var updatedBy: Long? = null
        protected set

    var deletedAt: ZonedDateTime? = null
        protected set

    var deletedBy: Long? = null
        protected set

    /**
     * 소프트 삭제. 이미 삭제된 document 에 재호출 시 IllegalStateException.
     */
    fun softDelete(userId: Long?) {
        check(deletedAt == null) { "Document is already soft-deleted" }
        deletedAt = ZonedDateTime.now()
        deletedBy = userId
    }

    val isDeleted: Boolean
        get() = deletedAt != null
}
