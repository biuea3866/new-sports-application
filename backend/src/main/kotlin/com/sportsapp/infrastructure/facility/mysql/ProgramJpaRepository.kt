package com.sportsapp.infrastructure.facility.mysql

import com.sportsapp.domain.facility.entity.Program
import org.springframework.data.jpa.repository.JpaRepository

interface ProgramJpaRepository : JpaRepository<Program, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Program?
    fun findByFacilityIdAndDeletedAtIsNull(facilityId: String): List<Program>
}
