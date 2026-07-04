package com.sportsapp.infrastructure.alerting.mysql

import com.sportsapp.domain.alerting.entity.Alert
import org.springframework.data.jpa.repository.JpaRepository

interface AlertJpaRepository : JpaRepository<Alert, Long> {

    /** soft-delete 컨벤션(JpaAuditingBase) — 삭제되지 않은 Alert만 조회한다. */
    fun findByIdAndDeletedAtIsNull(id: Long): Alert?
}
