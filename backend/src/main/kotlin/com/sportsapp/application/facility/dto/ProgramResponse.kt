package com.sportsapp.application.facility.dto

import com.sportsapp.domain.facility.entity.Program
import java.math.BigDecimal

/**
 * TDD "DTO 흐름 Request→Command→Entity→Response". Controller가 그대로 반환한다.
 */
data class ProgramResponse(
    val id: Long,
    val facilityId: String,
    val ownerUserId: Long,
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val capacity: Int,
    val durationMinutes: Int,
) {
    companion object {
        fun of(program: Program): ProgramResponse = ProgramResponse(
            id = program.id,
            facilityId = program.facilityId,
            ownerUserId = program.ownerUserId,
            name = program.name,
            description = program.description,
            price = program.price,
            capacity = program.capacity,
            durationMinutes = program.durationMinutes,
        )
    }
}
