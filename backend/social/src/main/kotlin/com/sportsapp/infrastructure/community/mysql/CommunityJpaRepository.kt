package com.sportsapp.infrastructure.community.mysql

import com.sportsapp.domain.community.entity.Community
import org.springframework.data.jpa.repository.JpaRepository

interface CommunityJpaRepository : JpaRepository<Community, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Community?
}
