package com.sportsapp.domain.facility.repository

import com.sportsapp.domain.facility.entity.Program

interface ProgramRepository {
    fun save(program: Program): Program
    fun findById(id: Long): Program?
    fun findByFacilityId(facilityId: String): List<Program>
}
